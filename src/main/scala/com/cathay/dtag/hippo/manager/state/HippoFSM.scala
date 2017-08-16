package com.cathay.dtag.hippo.manager.state

import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState

import scala.concurrent.duration._
import HippoFSM._

import scala.reflect.ClassTag

object HippoFSM {

  // FSM state
  sealed trait HippoState extends FSMState
  case object Sleep extends HippoState {
    override def identifier: String = "Sleep"
  }
  case object Running extends HippoState {
    override def identifier: String = "Running"
  }
  case object Missing extends HippoState {
    override def identifier: String = "Missing"
  }
  case object Dead extends HippoState {
    override def identifier: String = "Dead"
  }

  // FSM Data
  sealed trait HippoData
  case class Program(updatedAt: Long) extends HippoData
  case class Process(monitorPID: Int, updatedAt: Long)) extends HippoData

  // Hippo Domain Event
  sealed trait HippoEvent {
    val timestamp: Long = System.currentTimeMillis() / 1000
  }
  case class RunSuccess(monitorPID: Int) extends HippoEvent
  case class RunFail(monitorPID: Int) extends HippoEvent
  case object Kill extends HippoEvent
  case class Update(monitorPID: Option[Int]=None) extends HippoEvent
  case object NotFound extends HippoEvent
  case object ConfirmDead extends HippoEvent
  case object GiveUp extends HippoEvent

  // Command from outer
  sealed trait Command
  case class Register(config: HippoConfig)
  case class Start(checkInterval: Int)
  case object Stop
  case object GetState
}


class HippoFSM(conf: HippoConfig) extends PersistentFSM[HippoState, HippoData, HippoEvent]{


  override def persistenceId: String = conf.key

  when(Sleep) {
    case Event(Start(interval), _) =>
      //

      goto(Running) applying Run(pid)
  }

  when(Running, stateTimeout = FiniteDuration(conf.checkInterval, SECONDS)) {
    case Event(Stop, _) =>
      // sh kill.sh
      val isSuccess = 0
      val

    case Event(Kill, _) =>
      goto(Sleep) applying IdleData

    case Event(Update(Some(pid)), _) =>
      stay applying  ActiveHippo(pid)

    case Event(StateTimeout, _) =>
  }

  when(Missing) {
    case Event(Update(None), ActiveHippo(pid)) =>
      goto(Running) using ActiveHippo(pid)
    case Event(ConfirmDead, _) =>
      goto(Dead) using IdleHippo
  }

  when(Dead) {
    case Event(Run(pid), _) =>
      goto(Running) using ActiveHippo(pid)
    case Event(GiveUp, _) =>
      goto(Dead) using IdleHippo
  }

  whenUnhandled {
    case Event(GetState, stateData) =>
      stateData match {
        case ih @ IdleHippo =>
          sender() ! HippoService(hs.conf, ih.updatedAt, None, stateName)
        case ah @ ActiveHippo(pid) =>
          sender() ! HippoService(hs.conf, ah.updatedAt, Some(pid), stateName)
      }
      stay()
  }

  onTransition {
    case Running -> Missing =>
      //"ps aux".!
      self ! ConfirmDead
  }

  override def applyEvent(domainEvent: HippoEvent, currentData: HippoData): HippoData = ???
}
