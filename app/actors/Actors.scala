package actors

import akka.util.Timeout
import akka.actor._
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent.Akka
import akka.pattern.ask
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.Future
import play.api.libs.json.JsString
import scala.Some
import play.api.libs.json.JsObject
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by Corey Auger on 3/29/2014.
 */

// Messages Types.
case class NowConnected(enumerator:Enumerator[JsValue])
case class CannotConnect(msg: String)
case class Connect(userid: String)
case class Quit(userid: String)
case class UserSocketConnect(socket: Concurrent.Channel[JsValue])
case class UserSocketDisconnect(socket: Concurrent.Channel[JsValue])
case class JsonResponse(json: JsValue)
case class FutureJsonResponse(f: Future[JsValue])
case class JsonRequest(op: String, json: JsValue)



class UserActor(val id: String) extends Actor with ActorLogging{
  log.info(s"UserActor create: $id")
  var socketmap = Map[String,Concurrent.Channel[JsValue]]()

  def receive = {
    case jr:JsonRequest =>
      val slot = (jr.json \ "slot")
      val op = jr.op
      log.info(s"UserActor::receive::$op for slot: $slot")
      // additional operations other then book keeping..
      if( op == "??" ){
        val chatid = (jr.json \ "data" \ "chatid").as[String]
        val msg = (jr.json \ "data" \ "msg" \ "msg").as[String]
        val app = (jr.json \ "data" \ "msg" \ "app")
        // TODO: do stuff with protocol info...
      }

    case c: UserSocketConnect =>
      log.info(s"UserActor::UserSocketConnect")
      socketmap += context.sender.path.name -> c.socket
      sender ! c

    case d: UserSocketDisconnect =>
      log.info(s"UserActor::UserSocketDisconnect")
      socketmap -= context.sender.path.name
      sender ! d
      val numsockets = socketmap.size
      log.info(s"Num sockets left $numsockets for $id")
      if( socketmap.isEmpty ){
        // no more web socket connection. And this is a non walkaobut user.. so terminate
        SocketHandler.usermap -= id
        log.info(s"No more sockets USER SHUTDOWN.. good bye !")
        context.stop(self)
      }
  }
}





// TODO: simplify this with an akka "Agent"
// TODO: This lookup would lookup/find the actor on a production cluster
case class GetUserActor(id: String)
class LockActor extends Actor{
  def receive = {
    case GetUserActor(id) =>
      val actor = SocketHandler.usermap.get(id) match{
        case Some(actor) => actor
        case None =>
          println(s"CREATE NEW USER ACTOR: $id")
          val ar = Akka.system.actorOf(Props(new UserActor(id)),id)
          SocketHandler.usermap += id -> ar
          ar
      }
      sender ! actor
  }
}



class SocketHandler(val id: String)   extends Actor with ActorLogging{
  log.info(s"WebSocketActor create: $id")
  var members = Set.empty[String]
  val (socketEnumerator, socketChannel) = Concurrent.broadcast[JsValue]

  val userActor = {
    // TODO: this is where we will locate the "single" user actor on the cluster...
    implicit val timeout =  Timeout(3 seconds)
    SocketHandler.lockActor ? GetUserActor(id)
  }

  def receive = {
    case Connect(userid) =>
      // we are connected... so tell the sender
      val scopeSender = sender  // save the sender to use in future below
      userActor.onSuccess{
        case useractor:ActorRef =>
          useractor ! UserSocketConnect(socketChannel)
          scopeSender ! NowConnected(socketEnumerator)
      }

    case d: UserSocketDisconnect =>{
      // the user actor has removed a ref to this socket.. so we can die
      context.stop(self)  // stop myself.
    }

    case JsonRequest(op:String, json:JsValue) =>
        userActor.onSuccess {
          case useractor: ActorRef =>
            useractor ! JsonRequest(op, json)
        }

    case Quit(username) =>
      userActor.onSuccess {
        case useractor: ActorRef =>
          useractor ! UserSocketDisconnect(socketChannel)
      }
      socketChannel.end() // end the iteratee and close the socket

  }

}

object SocketHandler{
  implicit val timeout = Timeout(1.second)

  var connections = List[ActorRef]()

  // TODO: for now this is a hack memory storage...
  var usermap = Map[String,ActorRef]()
  val lockActor = Akka.system.actorOf(Props(new LockActor()))

  def cometSend(id: String, json: JsValue) ={
    // this is kinda hack...
    val op = (json \ "op").as[String]
    println(s"comet send doing $op")
    SocketHandler.usermap.get(id) match{
      case Some(actor) => println(s"found actor... $id"); actor ! JsonRequest(op, json)
      case None => println(s"Actor($id) NOT found..doing nothing!");
    }
  }

  def connect(id: String):scala.concurrent.Future[(Iteratee[JsValue,_],Enumerator[JsValue])] = {
    println(s"WebSocketHandler: $id")
    val wsActor = Akka.system.actorOf(Props(new SocketHandler(id)))
    //connections = connections :+ wsActor
    (wsActor ? Connect(id)).map {
      case NowConnected(enumerator) =>
        // Create an Iteratee to consume the feed
        val iteratee = Iteratee.foreach[JsValue] { event =>
        //println(event)
          wsActor ! JsonRequest((event \ "op").as[String], event)
        }.map { _ =>
          wsActor ! Quit(id)
        }
        (iteratee,enumerator)

      case Quit(id) =>
        connections = connections.filterNot(c => c == wsActor)
        val iteratee = Done[JsValue,Unit]((),Input.EOF)
        val enumerator =  Enumerator[JsValue](JsObject(Seq("op" -> JsString("done")))).andThen(Enumerator.enumInput(Input.EOF))
        (iteratee,enumerator)

      case CannotConnect(error) =>
        // Connection error
        // A finished Iteratee sending EOF
        val iteratee = Done[JsValue,Unit]((),Input.EOF)
        // Send an error and close the socket
        val enumerator =  Enumerator[JsValue](JsObject(Seq("error" -> JsString(error)))).andThen(Enumerator.enumInput(Input.EOF))
        (iteratee,enumerator)
    }

  }

  def connectComet(id: String) = {
    val wsActor = Akka.system.actorOf(Props(new SocketHandler(id)))
    (wsActor ? Connect(id)).map {
      case NowConnected(enumerator) =>
        // just return the enumerator for comet
        enumerator

      case Quit(id) =>
        connections = connections.filterNot(c => c == wsActor)
        val enumerator =  Enumerator[JsValue](JsObject(Seq("op" -> JsString("done")))).andThen(Enumerator.enumInput(Input.EOF))
        enumerator

      case CannotConnect(error) =>
        // Connection error
        val enumerator =  Enumerator[JsValue](JsObject(Seq("error" -> JsString(error)))).andThen(Enumerator.enumInput(Input.EOF))
        enumerator
    }
  }
}
