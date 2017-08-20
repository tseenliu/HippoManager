package com.cathay.dtag.hippo.manager.state

import akka.actor.{ActorRef, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

import HippoConfig.Command
import HippoConfig.Command._


object EntryStateActor {

  // Event
  sealed trait EntryEvent
  case class HippoAdded(config: HippoConfig) extends EntryEvent
  case class HippoRemoved(id: String) extends EntryEvent
  case class Operation(cmd: Command, id: String)
  case object GetNodeStatus

  // State
  case class HippoRef(config: HippoConfig, actor: Option[ActorRef]=None) {
    def isActive = actor.isDefined
  }

  case class HippoRepo(map: Map[String, HippoRef] = Map()) {

    def count: Int = map.size

    def addOne(config: HippoConfig, actor: Option[ActorRef]=None): HippoRepo =
      HippoRepo(map + (config.id -> HippoRef(config, actor)))

    def addConfigs(configs: List[HippoConfig]): HippoRepo = {
      val newRefMap = configs
        .filter(c => !map.contains(c.id))
        .map(c => c.id -> HippoRef(c, None)).toMap

      HippoRepo(map ++ newRefMap)
    }

    def initActors(createActor: HippoConfig => ActorRef): HippoRepo = {
      val newMap = map.mapValues {
        case HippoRef(config, None) =>
          val actorRef = createActor(config)
          HippoRef(config, Some(actorRef))
        case hr @ HippoRef(_, Some(_))  =>
          hr
      }
      HippoRepo(newMap)
    }

    def remove(id: String): HippoRepo = HippoRepo(map - id)

    def getActor(id: String): ActorRef = map(id).actor.get

    def containActor(id: String): Boolean =
      map.contains(id) && map(id).actor.isDefined

    def containActor(config: HippoConfig): Boolean =
      containActor(config.id)

    def configs: List[HippoConfig] =
      map.values.map(_.config).toList

    def actors: List[ActorRef] =
      map.values.map(_.actor).filter(_.isDefined).map(_.get).toList
  }

  // Response
  sealed trait Response
  object Response {
    case object HippoExists extends Response
    case object HippoNotFound extends Response
    case object EntryCmdSuccess extends Response
  }
}

class EntryStateActor(addr: String) extends PersistentActor {
  import EntryStateActor._
  import EntryStateActor.Response._

  import context.dispatcher

  var repo: HippoRepo = HippoRepo()

  def createActor(config: HippoConfig): ActorRef = {
    context.actorOf(Props(new HippoFSM(config)), name = config.id)
  }

  def takeSnapShot(): Unit = {
    if (repo.count % 5 == 0) {
      val configs = repo.configs
      saveSnapshot(configs)
    }
  }

  def updateRepo(evt: EntryEvent): Unit = evt match {
    case HippoAdded(config) if !repo.containActor(config) =>
      val actor = createActor(config)
      repo = repo.addOne(config, Some(actor))
      takeSnapShot()
    case HippoRemoved(id) if repo.containActor(id) =>
      repo = repo.remove(id)
      takeSnapShot()
  }

  override def persistenceId = addr

  override def receiveRecover = {
    case evt @ HippoAdded(config) =>
      println(s"Entry receive hippo added: $evt on recovering mood")
      repo = repo.addOne(config)
    case evt @ HippoRemoved(id) =>
      println(s"Entry receive hippo removed: $evt on recovering mood")
      repo = repo.remove(id)
    case SnapshotOffer(_, snapshot: List[HippoConfig]) =>
      println(s"Entry receive snapshot with data: $snapshot on recovering mood")
      repo = repo.addConfigs(snapshot)
    case RecoveryCompleted =>
      println("Recovery Completed and Now I'll init actors before switching to receiving mode")
      repo = repo.initActors(createActor)
  }

  override def receiveCommand = {
    case Register(conf) =>
      if (!repo.containActor(conf)) {
        persist(HippoAdded(conf)) { evt =>
          updateRepo(evt)
          sender() ! EntryCmdSuccess
        }
      } else {
        sender() ! HippoExists
      }
    case Remove(host, name) =>
      val id = HippoConfig.generateHippoID(host, name)

      if (repo.containActor(id)) {
        persist(HippoRemoved(id)) { evt =>
          repo.getActor(id) ! Delete
          updateRepo(evt)
          sender() ! EntryCmdSuccess
        }
      } else {
        sender() ! HippoNotFound
      }
    case Operation(GetStatus, id) =>
      if (repo.containActor(id)) {
        (repo.getActor(id) ? GetStatus) pipeTo sender()
      } else {
        sender() ! HippoNotFound
      }
    case Operation(cmd, id) =>
      if (repo.containActor(id)) {
        repo.getActor(id) ! cmd
        sender() ! EntryCmdSuccess
      } else {
        sender() ! HippoNotFound
      }
    case GetNodeStatus =>
      implicit val timeout = Timeout(5 seconds)
      val futureList = Future.traverse(repo.actors) { actor =>
        (actor ? GetStatus).mapTo[HippoInstance]
      }.map(list => list.map(inst => inst.conf.id -> inst).toMap)
      futureList pipeTo sender()
  }
}


