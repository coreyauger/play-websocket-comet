package controllers


import play.api.mvc.{WebSocket, Action, Controller}
import play.api.libs.json.{Json, JsValue}
import play.api.libs.iteratee.{Enumerator, Iteratee}
import scala.concurrent._
import play.api.libs.Comet
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import actors.SocketHandler

object Application extends Controller {


  def index = Action {
    Ok(views.html.index("Hello Play Framework"))
  }


  def ws(username: String) = WebSocket.async[JsValue] { request  =>
    println(s"call to websoket: $username")
    if( true ){
      SocketHandler.connect(username)
    }else{  // here is how you would fail out if you had some condition above.
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
    if( true ){
      val enumerator = Await.result(SocketHandler.connectComet(uuid), 3.seconds)
      Ok.chunked( enumerator &> Comet(callback = "parent.cometMessage"))
    }else { // here is how to fail out if you had some conditional above.
      println("Report some error.. perhaps not authorized ect ..")
      val out = Enumerator(Json.obj("op" -> "exception", "slot" -> "exception", "msg" -> "Not Authorized").asInstanceOf[JsValue]) >>> Enumerator.eof
      Ok.chunked(out &> Comet(callback = "parent.cometMessage"))
    }
  }

  def cometSend(uuid: String) = Action{ request  =>
    val data = request.body.asFormUrlEncoded.get.apply("data").head
    SocketHandler.cometSend(uuid,  Json.parse(data))
    println(s"comet Send got data: $data")
    Ok(Json.obj("status" -> "ack"))
  }

}