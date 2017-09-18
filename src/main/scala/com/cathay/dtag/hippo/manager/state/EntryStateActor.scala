package com.cathay.dtag.hippo.manager.state

import akka.actor.{ActorRef, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import com.cathay.dtag.hippo.manager.conf.{HippoConfig, HippoGroup, HippoInstance}
import com.cathay.dtag.hippo.manager.report.ReportMessage

import scala.concurrent.Future
import scala.concurrent.duration._


object EntryStateActor {

  // Event
  sealed trait EntryEvent
  case class HippoAdded(conf: HippoConfig) extends EntryEvent
  case class HippoRemoved(id: String) extends EntryEvent

  // State
  case class HippoRef(conf: HippoConfig, actor: Option[ActorRef]=None) {
    def isActive: Boolean = actor.isDefined
  }

  case class HippoRegistry(state: Map[String, HippoRef] = Map()) {

    def count: Int = state.size

    def addOne(conf: HippoConfig, actor: Option[ActorRef]=None): HippoRegistry =
      HippoRegistry(state + (conf.id -> HippoRef(conf, actor)))

    def addConfigs(configs: List[HippoConfig]): HippoRegistry = {
      val newRefMap = configs
        .filter(c => !state.contains(c.id))
        .map(c => c.id -> HippoRef(c, None)).toMap

      HippoRegistry(state ++ newRefMap)
    }

    def initActors(createActor: HippoConfig => ActorRef): HippoRegistry = {
      val registry = state.values.foldLeft(this) { (registry, hr) =>
        if (hr.isActive) {
          registry
        } else{
          val actor = createActor(hr.conf)
          registry.addOne(hr.conf, Some(actor))
        }
      }
      registry
    }

    def remove(id: String): HippoRegistry = HippoRegistry(state - id)
    def getActor(id: String): ActorRef = state(id).actor.get
    def hasRegistered(id: String): Boolean = state.contains(id)
    def hasRegistered(config: HippoConfig): Boolean = hasRegistered(config.id)
    def containActor(id: String): Boolean = hasRegistered(id) && state(id).isActive
    def containActor(config: HippoConfig): Boolean = containActor(config.id)
    def getConfigs: List[HippoConfig] = state.values.map(_.conf).toList
    def getActors: List[ActorRef] =
      state.values.filter(_.isActive).map(_.actor.get).toList
  }
}

class EntryStateActor(addr: String) extends PersistentActor {
  import EntryStateActor._
  import HippoConfig.HippoCommand._
  import HippoConfig.EntryCommand._
  import HippoConfig.Response._

  import context.dispatcher

  var registry: HippoRegistry = HippoRegistry()

  def createActor(conf: HippoConfig): ActorRef = {
    context.actorOf(Props(new HippoStateActor(conf)), name = conf.id)
  }

  def takeSnapShot(): Unit = {
    if (registry.count % 3 == 0) {
      val configs = registry.getConfigs
      saveSnapshot(configs)
    }
  }

  def updateRepo(evt: EntryEvent): Unit = evt match {
    case HippoAdded(conf) if !registry.containActor(conf) =>
      val actor = createActor(conf)
      registry = registry.addOne(conf, Some(actor))
      println(s"repoCount: ${registry.count}")
      takeSnapShot()
    case HippoRemoved(id) if registry.hasRegistered(id) =>
      registry = registry.remove(id)
      takeSnapShot()
  }

  override def persistenceId = addr

  override def receiveRecover = {
    case evt @ HippoAdded(config) if !registry.containActor(config) =>
      println(s"Entry receive hippo added: $evt on recovering mood")
      registry = registry.addOne(config)
    case evt @ HippoRemoved(id) =>
      println(s"Entry receive hippo removed: $evt on recovering mood")
      registry = registry.remove(id)
    case SnapshotOffer(_, snapshot: List[HippoConfig]) =>
      println(s"Entry receive snapshot with data: $snapshot on recovering mood")
      registry = registry.addConfigs(snapshot)
    case RecoveryCompleted =>
      println("Recovery Completed and Now I'll init actors before switching to receiving mode")
      registry = registry.initActors(createActor)
      println("init actors completed.")
  }

  // Receive CLI Common Command from user
  override def receiveCommand = {
    case Register(conf) =>
      if (!registry.hasRegistered(conf)) {
        persist(HippoAdded(conf)) { evt =>
          updateRepo(evt)
          sender() ! EntryCmdSuccess
        }
      } else {
        sender() ! HippoExists
      }

    case Remove(id) =>
      if (registry.hasRegistered(id)) {
        persist(HippoRemoved(id)) { evt =>
          registry.getActor(id) ! Delete
          updateRepo(evt)
          sender() ! EntryCmdSuccess
        }
      } else {
        sender() ! HippoNotFound
      }
    case GetNodeStatus =>
      // TODO: Cache result
      implicit val timeout = Timeout(5 seconds)
      val futureList = Future.traverse(registry.getActors) { actor =>
          (actor ? GetStatus).mapTo[HippoInstance]
        }.map { list =>
          list.map(inst => inst.conf.id -> inst).toMap
        }.map(x => HippoGroup(addr, x))

      futureList pipeTo sender()

//    case Operation(Report(updatedAt), id) =>
//      if (registry.containActor(id)) {
//        registry.getActor(id) ! Report(updatedAt)
//      } else {
//        sender() ! HippoNotFound
//      }
    case Operation(cmd, id) =>
      implicit val timeout = Timeout(5 seconds)
      if (registry.containActor(id)) {
        (registry.getActor(id) ? cmd) pipeTo sender()
      } else {
        sender() ! HippoNotFound
      }
    case msg: ReportMessage =>
      val id = HippoConfig.generateHippoID(msg.host, msg.service_name)
      if (registry.containActor(id)) {
        //self ! Operation(Report(rt.toLong), id)
        if (registry.containActor(id)) {
          registry.getActor(id) ! Report(msg.exec_time.toLong)
        }
      } else {
        println(s"${msg.service_name} not register, or state actor is not running.")
      }

  }
}


