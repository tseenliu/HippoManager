package com.cathay.dtag.hippo.manager.api

import akka.actor.ActorSystem
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import com.cathay.dtag.hippo.manager.conf.HippoConfig.EntryCommand.GetNodeStatus
import com.cathay.dtag.hippo.manager.conf._
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContext, Future}
import spray.json._
import akka.pattern.ask


object APIServer extends App with ManagerRoute {
  val config = ConfigFactory.load()
  override implicit val system = ActorSystem("ClusterSystem", config)
  override implicit val materializer: ActorMaterializer = ActorMaterializer()
  override implicit val ec: ExecutionContext = system.dispatcher

  implicit def exceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case _ =>
        extractUri { uri =>
          val message = "Bad numbers, bad result!!!"
          complete(HttpResponse(StatusCodes.InternalServerError, entity = message))
        }
    }

  val coordAddr = "akka.tcp://ClusterSystem@127.0.0.1:2551"
  val coordinator = system.actorSelection(s"$coordAddr/user/coordinator")

  val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
  println(s"Server online at http://localhost:8080...")
}
