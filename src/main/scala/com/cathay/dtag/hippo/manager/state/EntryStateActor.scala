package com.cathay.dtag.hippo.manager.state

import akka.actor.{ActorRef, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.cathay.dtag.hippo.manager.conf.{HippoConfig, HippoInstance, HippoGroup}

import scala.concurrent.Future
import scala.concurrent.duration._


object EntryStateActor {

  // Event
  sealed trait EntryEvent
  case class HippoAdded(config: HippoConfig) extends EntryEvent
  case class HippoRemoved(id: String) extends EntryEvent

  // State
  case class HippoRef(config: HippoConfig, actor: Option[ActorRef]=None) {
    def isActive: Boolean = actor.isDefined
  }

  case class HippoRepo(state: Map[String, HippoRef] = Map()) {

    def count: Int = state.size

    def addOne(config: HippoConfig, actor: Option[ActorRef]=None): HippoRepo =
      HippoRepo(state + (config.id -> HippoRef(config, actor)))

    def addConfigs(configs: List[HippoConfig]): HippoRepo = {
      val newRefMap = configs
        .filter(c => !state.contains(c.id))
        .map(c => c.id -> HippoRef(c, None)).toMap

      HippoRepo(state ++ newRefMap)
    }

    def initActors(createActor: HippoConfig => ActorRef): HippoRepo = {
      val repo = state.values.foldLeft(this) { (repo, hr) =>
        if (hr.isActive) {
          repo
        } else{
          val actor = createActor(hr.config)
          repo.addOne(hr.config, Some(actor))
        }
      }
      repo
    }

    def remove(id: String): HippoRepo = HippoRepo(state - id)
    def getActor(id: String): ActorRef = state(id).actor.get
    def hasRegistered(id: String): Boolean = state.contains(id)
    def hasRegistered(config: HippoConfig): Boolean = hasRegistered(config.id)
    def containActor(id: String): Boolean = hasRegistered(id) && state(id).isActive
    def containActor(config: HippoConfig): Boolean = containActor(config.id)
    def configs: List[HippoConfig] = state.values.map(_.config).toList
    def actors: List[ActorRef] =
      state.values.filter(_.isActive).map(_.actor.get).toList
  }
}

class EntryStateActor(addr: String) extends PersistentActor {
  import EntryStateActor._
  import HippoConfig.HippoCommand._
  import HippoConfig.EntryCommand._
  import HippoConfig.Response._

  import context.dispatcher

  var repo: HippoRepo = HippoRepo()

  def createActor(config: HippoConfig): ActorRef = {
    context.actorOf(Props(new HippoStateActor(config)), name = config.id)
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
    case HippoRemoved(id) if repo.hasRegistered(id) =>
      repo = repo.remove(id)
      takeSnapShot()
  }

  override def persistenceId = addr

  override def receiveRecover = {
    case evt @ HippoAdded(config) if !repo.containActor(config) =>
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
      println("init actors completed.")
  }

  override def receiveCommand = {
    case Register(conf) =>
      if (!repo.hasRegistered(conf)) {
        persist(HippoAdded(conf)) { evt =>
          updateRepo(evt)
          sender() ! EntryCmdSuccess
        }
      } else {
        sender() ! HippoExists
      }
    case Remove(host, name) =>
      val id = HippoConfig.generateHippoID(host, name)

      if (repo.hasRegistered(id)) {
        persist(HippoRemoved(id)) { evt =>
          repo.getActor(id) ! Delete
          updateRepo(evt)
          sender() ! EntryCmdSuccess
        }
      } else {
        sender() ! HippoNotFound
      }
    case Operation(GetStatus, id) =>
      implicit val timeout = Timeout(5 seconds)
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
      // TODO: Cache result
      implicit val timeout = Timeout(5 seconds)
      val futureList = Future.traverse(repo.actors) { actor =>
          (actor ? GetStatus).mapTo[HippoInstance]
        }.map { list =>
          list.map(inst => inst.conf.id -> inst).toMap
        }.map(x => HippoGroup(x))

      futureList pipeTo sender()
  }
}


