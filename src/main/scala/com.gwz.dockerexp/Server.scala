package com.gwz.dockerexp

import akka.http.Http
import akka.http.model._
import akka.http.model.HttpMethods._
import akka.stream.scaladsl.Flow
import akka.stream.FlowMaterializer
import akka.util.Timeout
import scala.concurrent.duration._

import akka.actor._
import com.typesafe.config.{ Config, ConfigFactory }
import scala.sys.process._

trait DocSvr {
	implicit var system:ActorSystem = null
	def appArgs:Array[String] = Array.empty[String]
	var name = ""
	val myHostname = java.net.InetAddress.getLocalHost().getHostAddress()
	var myHttpUri = ""
	var akkaUri:Address = null

	def init() {
		NodeConfig parseArgs appArgs map{ nc =>
			val c = nc.config
			//println(c)
			name = c.getString("dkr.name")
			val httpPort = c.getInt("http.port")
			val akkaPort = c.getInt("dkr.port")
			myHttpUri = "http://"+myHostname+":"+httpPort+"/"
			system = ActorSystem( "dockerexp", c)

			akkaUri = system.asInstanceOf[ExtendedActorSystem].provider.getDefaultAddress
			println("AKKA: "+akkaUri)

			system.actorOf(Props(new TheActor(this)), "dockerexp")

			HttpService(this, myHostname, httpPort)
		}
	}
}

case class HttpService(svr:DocSvr, iface:String, port:Int) {

	implicit val system = svr.system
	implicit val materializer = FlowMaterializer()
	implicit val t:Timeout = 15.seconds

	println("HTTP Service on port "+port)

	val requestHandler: HttpRequest ⇒ HttpResponse = {
		case HttpRequest(GET, Uri.Path("/ping"), _, _, _)  => HttpResponse(entity = s"""{"resp":"${svr.name} says pong"}""")
		case _: HttpRequest => HttpResponse(404, entity = "Unknown resource!")
	}

	val serverBinding = Http(system).bind(interface = iface, port = port)
	serverBinding.connections foreach { connection => connection handleWith { Flow[HttpRequest] map requestHandler } }
}

object Go extends App with DocSvr {
	override def appArgs = args
	init()
}