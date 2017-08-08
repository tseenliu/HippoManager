package com.cathay.dtag.hippo.manager

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberRemoved, MemberUp}
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata._
import com.typesafe.config.ConfigFactory
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._


class Coordinator extends Actor with ActorLogging {
  import HipposState._

  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val timeout = Timeout(5 seconds)

  implicit val node = Cluster(context.system)

  val addr: String = node.selfAddress.toString

  val localSnapshot: ActorRef = context.actorOf(
    Props(new HippoSnapshot(addr)), name = "local-snapshot")

  val replicator: ActorRef = DistributedData(context.system).replicator
  val HipposStateKey = LWWMapKey[String, HipposState]("hippsState")


  override def preStart(): Unit = {
    node.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent])

    self ! UpdateStates
  }

  override def postStop(): Unit = {
    node.unsubscribe(self)
  }

  override def receive: Receive = {
    case MemberUp(member) =>
      log.info("Member is Up: {}", member.address)

    case MemberRemoved(member, previousStatus) =>
      log.info("Member is Removed: {} after {}",
        member.address, previousStatus)

      (localSnapshot ? GetLocalState).mapTo[HipposState].map { state =>
        replicator ! Update(HipposStateKey,
          LWWMap.empty[String, HipposState], WriteLocal) { m =>
          val memberAddr = member.address.toString
          if (m.contains(memberAddr)) {
            m - memberAddr
          } else {
            m
          }
        }
      }

    case cmd: Cmd =>
      localSnapshot ! cmd

    case UpdateStates =>
      (localSnapshot ? GetLocalState).mapTo[HipposState].map { state =>
        val writeAll = WriteAll(timeout = 5.seconds)
        replicator ! Update(HipposStateKey,
          LWWMap.empty[String, HipposState], writeAll) { m =>
          m + (addr -> state)
        }
      }

    case x: UpdateResponse[_] =>
      //print("UpdateResponse", x.key)

    case "print_local" =>
      (localSnapshot ? GetLocalState).foreach(println)

    case "print_global" =>
      replicator ! Get(HipposStateKey, ReadLocal, request = Some(sender()))

    case g @ GetSuccess(HipposStateKey, Some(replyTo: ActorRef)) =>
      println("GetSuccess ddata...")
      val value = g.get(HipposStateKey)
      println(value)
      replyTo ! value.entries

    case GetFailure(HipposStateKey, Some(replyTo: ActorRef)) =>
      println("GetFailure ddata...")
//      (localSnapshot ? GetLocalState).mapTo[HipposState]
//        .map(s => Map(addr -> s)) pipeTo replyTo

    case NotFound(HipposStateKey, Some(replyTo: ActorRef)) =>
      println("NotFound ddata...")
//      (localSnapshot ? GetLocalState).mapTo[HipposState]
//        .map(s => Map(addr -> s)) pipeTo replyTo
  }
}

object Coordinator {
  def initiate(port: Int): ActorRef = {
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port")
      .withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)

    system.actorOf(Props[Coordinator], name="coordinator")
  }
}
