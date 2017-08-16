package com.cathay.dtag.hippo.manager.state

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.pattern.ask
import akka.util.Timeout

import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}


case class HipposListState(state: Map[String, HippoService]=Map()) {

  def update(serv: HippoService): HipposListState = {
    copy(state + (serv.key -> serv))
  }

  def remove(serv: HippoService): HipposListState = {
    copy(state - serv.key)
  }
}

class EntryStateActor(addr: String) extends PersistentActor {
  import HippoService._

  override def persistenceId: String = addr

  var state = HipposListState()

  var hippoFSMs: Map[String, ActorRef] = Map[String, ActorRef]()
    //state.state.mapValues(hs => context.actorOf(Props(new HippoStateActor(hs))))

  def createActor(serv: HippoService): ActorRef = {
    context.actorOf(Props(new HippoFSM(serv)), name = serv.key)
  }

  override def receiveRecover = {
    case Operation(serv, action) =>
      if (!hippoFSMs.contains(serv.key)) {
        val hippoActor = createActor(serv)
        hippoFSMs = hippoFSMs + (serv.key -> hippoActor)

      }
  }

  override def receiveCommand = {
    case Register(conf: HippoConfig) =>
      val serv = HippoService(conf)
      state.update(serv)
      hippoFSMs = hippoFSMs + (serv.key -> createActor(serv))

    case Operation(serv, action) =>
      if (hippoFSMs.contains(serv.key)) {
        hippoFSMs(serv.key) ! action
      } else {
        println(s"${serv.key} not found.")
      }
  }
}

object EntryStateActor extends App {
  import HippoService._

  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val timeout = Timeout(5 seconds)

  val system = ActorSystem("entry")

  val hc = HippoConfig("localhost", "batchetl.journey", "/hippos_batchetl_journey")
  val hs = HippoService(hc)
  println(hs)

  val stateFSM = system.actorOf(Props(new HippoFSM(hs)), name = "state-fsm")

  stateFSM ! Run(450)
  stateFSM ! Kill
  Thread.sleep(1000)
  stateFSM ! Run(555)
  (stateFSM ? GetState).onComplete {
    case Success(hs) => println("get success" + hs)
    case Failure(t) => println("An error has occured: " + t.getMessage)
  }
}
