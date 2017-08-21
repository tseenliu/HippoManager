package com.cathay.dtag.hippo.manager.core

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberRemoved, MemberUp}
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata._
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

import com.cathay.dtag.hippo.manager.conf._
import com.cathay.dtag.hippo.manager.state.EntryStateActor
import com.cathay.dtag.hippo.manager.conf.HippoConfig.EntryCommand
import com.cathay.dtag.hippo.manager.conf.HippoConfig.EntryCommand.GetNodeStatus


class Coordinator extends Actor with ActorLogging {
  import HippoConfig.CoordCommand._

  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val timeout = Timeout(5 seconds)

  implicit val node = Cluster(context.system)
  val addr: String = node.selfAddress.toString

  val entry: ActorRef = context.actorOf(
    Props(new EntryStateActor(addr)), name = "entry-state")

  val replicator: ActorRef = DistributedData(context.system).replicator
  val HippoGroupKey = LWWMapKey[String, HippoGroup]("hippoGroup")

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

      replicator ! Update(HippoGroupKey,
        LWWMap.empty[String, HippoInstance], WriteLocal) { m =>
        val memberAddr = member.address.toString
        if (m.contains(memberAddr)) m - memberAddr else m
      }

    case cmd: EntryCommand =>
      (entry ? cmd) pipeTo sender()

    case UpdateStates =>
      (entry ? GetNodeStatus).mapTo[HippoGroup].map { group =>
        val writeAll = WriteAll(timeout = 5.seconds)
        replicator ! Update(HippoGroupKey,
          LWWMap.empty[String, HippoGroup], writeAll)(_ + (addr -> group))
      }

    case x: UpdateResponse[_] =>
      //print("UpdateResponse", x.key)

    case PrintNodeStatus =>
      (entry ? GetNodeStatus).mapTo[HippoGroup]
        .map(_.group.values)
        .foreach(println)

    case PrintClusterStatus =>
      replicator ! Get(HippoGroupKey, ReadLocal, request = Some(sender()))

    case g @ GetSuccess(HippoGroupKey, Some(replyTo: ActorRef)) =>
      println("GetSuccess ddata...")
      val value = g.get(HippoGroupKey)
      println(value)
      replyTo ! value.entries

    case GetFailure(HippoGroupKey, Some(replyTo: ActorRef)) =>
      println("GetFailure ddata...")

    case NotFound(HippoGroupKey, Some(replyTo: ActorRef)) =>
      println("NotFound ddata...")
  }
}

object Coordinator {
  def initiate(port: Int): ActorRef = {
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port")
      .withFallback(ConfigFactory.load().getConfig("coordinator"))

    val system = ActorSystem("ClusterSystem", config)

    system.actorOf(Props[Coordinator], name="coordinator")
  }
}
