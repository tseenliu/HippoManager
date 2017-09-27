package com.cathay.dtag.hippo.manager.api

import akka.actor.{ActorRef, ActorSelection, ActorSystem}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.cathay.dtag.hippo.manager.core.env.EnvLoader
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext


object APIServer extends EnvLoader {
  def main(args: Array[String]): Unit = {
    configDir = if (args.length > 0) args(0) else "config"
    val clusterConfig = getConfig("cluster")
    val serviceConfig = getConfig("service").resolve()
    val server = new APIServer(clusterConfig, serviceConfig)
    server.run
  }
}

class APIServer(clusterConfig: Config,
                serviceConfig: Config) extends APIRoute {

  override implicit val system = ActorSystem("ClusterSystem", clusterConfig)
  override implicit val materializer: ActorMaterializer = ActorMaterializer()
  override implicit val ec: ExecutionContext = system.dispatcher

  val coordAddr: String = serviceConfig.getString("coordinator.address")
  def coordinator: ActorSelection = system.actorSelection(s"$coordAddr/user/coordinator")

  val server: Config = serviceConfig.getConfig("api")
  val host: String = server.getString("host")
  val port: Int = server.getInt("port")
  override val version: String = server.getString("version")
  println(version)


  def run: Unit = {
    val bindingFuture = Http().bindAndHandle(route, host, port)
    println(s"Server online at http://$host:$port...")
  }
}

