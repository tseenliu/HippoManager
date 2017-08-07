package com.cathay.dtag.hippo.manager

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


case class Bid(userId: String, offer: Int)
case object GetBids
case class Bids(bids: List[Bid])

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val bidFormat = jsonFormat2(Bid)
  implicit val bidsFormat = jsonFormat1(Bids)
}

class Auction extends Actor with ActorLogging {
  var bids = List.empty[Bid]
  def receive = {
    case bid @ Bid(userId, offer) =>
      bids = bids :+ bid
      log.info(s"Bid complete: $userId, $offer")
    case GetBids => sender() ! Bids(bids)
    case _ => log.info("Invalid message")
  }
}

trait RestAPI extends Directives with JsonSupport {
  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val ec: ExecutionContext

  val auction: ActorRef

  val route =
    path("auction") {
      (put & entity(as[Bid])) { bid =>
        auction ! bid
        complete(StatusCodes.Accepted, "bid placed")
      } ~
      get {
        implicit val timeout: Timeout = 5.seconds

        val bids: Future[Bids] = (auction ? GetBids).mapTo[Bids]
        complete(bids)
      }
    }

}

object WebServer extends App with RestAPI {
  override implicit val system: ActorSystem = ActorSystem("web-server")
  override implicit val materializer: ActorMaterializer = ActorMaterializer()
  override implicit val ec: ExecutionContext = system.dispatcher

  val auction = system.actorOf(Props[Auction], "auction")

  val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
  println(s"Server online at http://localhost:8080\nPress RETURN to stop...")


}
