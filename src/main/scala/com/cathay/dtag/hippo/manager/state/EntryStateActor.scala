package com.cathay.dtag.hippo.manager.state

import akka.actor.{ActorContext, ActorRef, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import HippoConfig.Command
import HippoConfig.Command._

object EntryStateActor {

  // Event
  sealed trait EntryEvent
  case class AddHippo(config: HippoConfig) extends EntryEvent
  case class RemoveHippo(id: String) extends EntryEvent
  case class Operation(cmd: Command, id: String)

  // State
  case class HippoRef(config: HippoConfig, actor: ActorRef)

  case class HippoRepo(map: Map[String, HippoRef] = Map()) {

    def count: Int = map.size

    def addActor(config: HippoConfig, actor: ActorRef): HippoRepo =
      HippoRepo(map + (config.id -> HippoRef(config, actor)))

    def removeActor(id: String): HippoRepo = HippoRepo(map - id)

    def getActor(id: String): ActorRef = map(id).actor

    def containActor(id: String): Boolean = map.contains(id)

    def containActor(config: HippoConfig): Boolean = map.contains(config.id)
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

  var repo: HippoRepo = HippoRepo()

  def createActor(config: HippoConfig): ActorRef = {
    context.actorOf(Props(new HippoFSM(config)), name = config.id)
  }

  def updateRepo(evt: EntryEvent): Unit = evt match {
    case AddHippo(config) =>
      val actor = createActor(config)
      repo = repo.addActor(config, actor)
      takeSnapShot()
    case RemoveHippo(id) =>
      repo = repo.removeActor(id)
      takeSnapShot()
  }

  override def persistenceId = addr

  override def receiveRecover = {
    case evt: EntryEvent =>
      println(s"Entry receive $evt on recovering mood")
      updateRepo(evt)
    case SnapshotOffer(_, snapshot: List[HippoConfig]) =>
      println(s"Entry receive snapshot with data: $snapshot on recovering mood")
      repo = recoverFromConfigs(snapshot)
    case RecoveryCompleted =>
      println("Recovery Completed and Now I'll switch to receiving mode")
  }

  override def receiveCommand = {
    case Register(conf) =>
      if (!repo.containActor(conf)) {
        persist(AddHippo(conf)) { evt =>
          updateRepo(evt)
          sender() ! EntryCmdSuccess
        }
      } else {
        sender() ! HippoExists
      }
    case Remove(host, name) =>
      val id = HippoConfig.generateHippoID(host, name)

      if (repo.containActor(id)) {
        persist(RemoveHippo(id)) { evt =>
          repo.getActor(id) ! Delete
          updateRepo(evt)
          sender() ! EntryCmdSuccess
        }
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
  }

  def takeSnapShot(): Unit = {
    if (repo.count % 5 == 0) {
      val configs = repo.map.values.map(_.config).toList
      saveSnapshot(configs)
    }
  }

  def recoverFromConfigs(configs: List[HippoConfig]): HippoRepo = {
    val repoMap = configs.map { config =>
      val actor = createActor(actor)
      (config.id, HippoRef(config, actor))
    }.toMap
    HippoRepo(repoMap)
  }
}


