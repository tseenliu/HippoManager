package com.cathay.dtag.hippo.manager.app

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.ExceptionHandler
import akka.stream.ActorMaterializer
import com.cathay.dtag.hippo.manager.api.APIRoute
import com.typesafe.config.ConfigFactory
import spray.json._

import scala.concurrent.ExecutionContext


object APIServer extends App with APIRoute {
  val config = ConfigFactory.load()
  override implicit val system = ActorSystem("ClusterSystem", config)
  override implicit val materializer: ActorMaterializer = ActorMaterializer()
  override implicit val ec: ExecutionContext = system.dispatcher

  implicit def exceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case ex =>
        extractUri { uri =>
          val message = "Bad numbers, bad result!!!"
          complete(StatusCodes.InternalServerError, JsObject(
            "message" -> JsString(ex.getMessage)
          ))
        }
    }

  val coordAddr = "akka.tcp://ClusterSystem@127.0.0.1:2551"
  val coordinator = system.actorSelection(s"$coordAddr/user/coordinator")

  val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
  println(s"Server online at http://localhost:8080...")
}