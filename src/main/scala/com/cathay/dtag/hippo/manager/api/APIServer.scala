package com.cathay.dtag.hippo.manager.api

import akka.actor.{ActorRef, ActorSelection, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.ExceptionHandler
import akka.stream.ActorMaterializer
import com.cathay.dtag.hippo.manager.core.env.EnvLoader
import com.typesafe.config.Config
import spray.json.{JsObject, JsString}

import scala.concurrent.ExecutionContext


object APIServer extends EnvLoader {
  def main(args: Array[String]): Unit = {
    configDir = if (args.length > 0) args(0) else "config"
    val clusterConfig = getConfig("cluster").resolve()
    val serviceConfig = getConfig("service")
    val server = new APIServer(clusterConfig, serviceConfig)
    server.run
  }
}

class APIServer(clusterConfig: Config,
                serviceConfig: Config) extends APIRoute {

  val sysName = clusterConfig.getString("system-name")
  override implicit val system = ActorSystem(sysName, clusterConfig)
  override implicit val materializer: ActorMaterializer = ActorMaterializer()
  override implicit val ec: ExecutionContext = system.dispatcher

  // Coordinator setting
  def setCoordAddress: String = {
    val coord: Config = serviceConfig.getConfig("coordinator")
    val host: String = coord.getString("host")
    val port: Int = coord.getInt("port")
    s"akka.tcp://$sysName@$host:$port"
  }
  val coordAddress: String = setCoordAddress

  // API setting
  val server: Config = serviceConfig.getConfig("api")
  val host: String = server.getString("host")
  val port: Int = server.getInt("port")
  override val version: String = server.getString("version")

  implicit def exceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case ex =>
        complete(StatusCodes.InternalServerError, JsObject(
          "message" -> JsString(ex.getMessage)
        ))
    }

  def run: Unit = {
    val bindingFuture = Http().bindAndHandle(route, host, port)
    println(s"Server online at http://$host:$port...")
  }
}

