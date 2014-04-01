package controllers

import _root_.actors.WebSocketHandler
import _root_.securesocial.core.SecureSocial.UserAwareAction
import _root_.service.Neo4JUserService
import play.api.mvc.{WebSocket, Action, Controller}
import play.api.libs.json.{Json, JsValue}
import play.api.libs.iteratee.{Enumerator, Iteratee}
import scala.concurrent._
import play.api.libs.Comet

object Application extends Controller {


  def index = Action {
    Ok(views.html.index("Hello Play Framework"))
  }


  def api(uuid: String) = WebSocket.async[JsValue] { request  =>
    println(s"call to websoket: $uuid")
    if( true ){   // TODO: some condition here.
      SocketHandler.connect(uuid)
    }else{
      println("Report some error.. perhaps not authorized ect ..")
      val in = Iteratee.foreach[JsValue] { event => }
      val out = Enumerator(Json.obj( "op" -> "exception", "slot" -> "exception", "msg" -> "Not Authorized" ).asInstanceOf[JsValue] ) >>> Enumerator.eof
      future{
        (in, out)
      }
    }
  }


  // NOTE: DO NOT route this call through nginx
  // See my blog post on the matter => http://affinetechnology.blogspot.ca/2014/03/play-framework-comet-chunking-support.html
  def comet(uuid: String) = Action{
    println(s"call to comet: $uuid")
    if( Neo4JUserService.auth(uuid, token)){
      val enumerator = Await.result(SocketHandler.connectComet(uuid), 3.seconds)
      Ok.chunked( enumerator &> Comet(callback = "parent.cometMessage"))
    }else {
      println("Report some error.. perhaps not authorized ect ..")
      val out = Enumerator(Json.obj("op" -> "exception", "slot" -> "exception", "msg" -> "Not Authorized").asInstanceOf[JsValue]) >>> Enumerator.eof
      Ok.chunked(out &> Comet(callback = "parent.cometMessage"))
    }
  }

  def cometSend(uuid: String) = Action.async{ request  =>
    val data = request.body.asFormUrlEncoded.get.apply("data").head
    val credFuture = SocketHandler.getUserCreds()(request)
    val p = Promise[play.api.mvc.SimpleResult]
    credFuture.onSuccess{
      case (uuid: String, token: String) =>
        WebSocketHandler.cometSend(uuid,  Json.parse(data))
        println(s"comet Send got data: $data")
        p.success( Ok(Json.obj("status" -> "ack")) )
    }
    p.future
  }

}