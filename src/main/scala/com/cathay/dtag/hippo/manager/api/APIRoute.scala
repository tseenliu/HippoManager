package com.cathay.dtag.hippo.manager.api

import akka.actor.{ActorRef, ActorSelection, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import akka.pattern.ask
import com.cathay.dtag.hippo.manager.conf.{HippoConfig, HippoGroup, HippoInstance}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import spray.json._

/**
  *** Service API
  * # Prefix
  * /hippo/[version]/services
  *
  * # Routes
  * 1. Register hippo:
  *   POST /
  * 2. Get cluster status:
  *   GET  /
  * 3. Get node status:
  *   GET  /node
  * 4. Start hippo
  *   POST /start
  * 5. Restart hippo
  *   POST /restart
  * 6. Stop hippo
  *   POST /stop
  * 7. Get hippo status:
  *   GET  /host/:host/name/:name
  * 8. Remove hippo
  *   DELETE /host/:host/name/:name
  */

trait APIRoute extends Directives with HippoJsonProtocol {
  import HippoConfig.CoordCommand._
  import HippoConfig.EntryCommand._
  import HippoConfig.HippoCommand._
  import HippoConfig.Response._

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val ec: ExecutionContext
  implicit val timeout = Timeout(5 seconds)
  val version = "v1.0.0"

  val coordinator: ActorSelection

  def handleResponse(x: Any): Route = x match {
    case EntryCmdSuccess | StateCmdSuccess =>
      complete(StatusCodes.OK, JsObject(
        "message" -> JsString("Command deliver successfully.")
      ))
    case StateCmdFailure =>
      complete(StatusCodes.BadRequest, JsObject(
        "message" -> JsString("Command failed.")
      ))
    case HippoExists =>
      complete(StatusCodes.BadRequest, JsObject(
        "message" -> JsString("Hippo exists.")
      ))
    case HippoNotFound =>
      complete(StatusCodes.NotFound, JsObject(
        "message" -> JsString("Hippo is not found.")
      ))
    case StateCmdUnhandled =>
      complete(StatusCodes.BadRequest, JsObject(
        "message" -> JsString("Command can not be handled at this state.")
      ))
  }

  def commandRoute: Route =
    (post & entity(as[CommandParams])) { cmdParams =>
      val id = HippoConfig.generateHippoID(cmdParams.host, cmdParams.serviceName)

      path("start") {
        // 4. Start hippo
        println(s"start ${cmdParams.serviceName}")
        val op = Operation(Start(cmdParams.interval), id)
        onSuccess(coordinator ? op)(handleResponse)
      } ~
      path("restart") {
        // 5. Restart hippo
        println(s"restart ${cmdParams.serviceName}")
        val op = Operation(Restart(cmdParams.interval), id)
        onSuccess(coordinator ? op)(handleResponse)
      } ~
      path("stop") {
        // 6. Stop hippo
        println(s"stop ${cmdParams.serviceName}")
        val op = Operation(Stop, id)
        onSuccess(coordinator ? op)(handleResponse)
      }
    }

  def instanceRoute(id: String): Route = {
    /**
      * ### prefix
      *   1. /services/host/:host/name/:name
      *   2. /services/instances/:id
      */
    pathEnd {
      get {
        // 7. Get hippo status
        val op = Operation(GetStatus, id)
        onSuccess(coordinator ? op) {
          case instance: HippoInstance =>
            complete(instance)
          case x =>
            handleResponse(x)
        }
      } ~
      delete {
        // 8. Remove hippo
        onSuccess(coordinator ? Remove(id))(handleResponse)
      }
    }
  }

  val route =
    pathPrefix("hippo" / version / "services") {
      pathEnd {
        (post & entity(as[HippoConfig])) { config =>
          // 1. Register hippo
          onSuccess(coordinator ? Register(config))(handleResponse)
        } ~
        get {
          // 2. Get cluster status
          complete {
            (coordinator ? GetClusterStatus)
              .mapTo[Map[String, HippoGroup]].map(_.values)
          }
        }
      } ~
      path("node") {
        get {
          // 3. Get node status
          complete {
            (coordinator ? GetNodeStatus).mapTo[HippoGroup]
          }
        }
      } ~ commandRoute ~
      pathPrefix("host" / Segment / "name" / Segment) { (host, name) =>
        val id = HippoConfig.generateHippoID(host, name)
        instanceRoute(id)
      } ~
      pathPrefix("instances" / Segment) { id =>
        instanceRoute(id)
      }
    }
}
