package com.cathay.dtag.hippo.manager.state

import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState

import scala.concurrent.duration._
import HippoStateActor._

import scala.reflect.ClassTag
import scala.reflect._
import scala.concurrent.duration._


object HippoStateActor {
  import HippoConfig._

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
  sealed trait HippoData {
    val updatedAt: Long
    val interval: Long
    val retry: Int

  }
  case class Program(interval: Long, updatedAt: Long, retry: Int=0) extends HippoData
  case class Process(monitorPID: Int, interval: Long, updatedAt: Long) extends HippoData {
    override val retry: Int = 0
  }

  sealed trait HippoEvent{
    val timestamp: Long = getCurrentTime
  }
  case class RunSuccess(monitorPID: Int, interval: Option[Long]=None) extends HippoEvent
  case class RunFail() extends HippoEvent
  case class KillSuccess() extends HippoEvent
  case class Confirm(isRunning: Boolean) extends HippoEvent
  case object Found extends HippoEvent
  case object NotFound extends HippoEvent
  case class GiveUp() extends HippoEvent
}


class HippoStateActor(conf: HippoConfig) extends PersistentFSM[HippoState, HippoData, HippoEvent] {

  import HippoStateActor._
  import HippoConfig._
  import HippoConfig.Command._

  val CHECK_TIMER: String = "check_timeout"

  // TODO: Start, Restart, Stop and Check command with Hippo Config
  val controller = new CommandController(conf)

  override def persistenceId: String = conf.id

  override def domainEventClassTag: ClassTag[HippoEvent] = classTag[HippoEvent]

  override def applyEvent(evt: HippoEvent, currData: HippoData): HippoData = {
    evt match {
      case RunSuccess(pid, interval) =>
        val checkInterval = interval.getOrElse(currData.interval)
        Process(pid, checkInterval, evt.timestamp)
      case Confirm(true) =>
        val proc = currData.asInstanceOf[Process]
        Process(proc.monitorPID, proc.interval, evt.timestamp)
      case (KillSuccess() | Confirm(false)) =>
        Program(currData.interval, evt.timestamp)
      case RunFail() =>
        Program(currData.interval, evt.timestamp, currData.retry + 1)
      case GiveUp() =>
        Program(currData.interval, evt.timestamp, currData.retry)
      case (NotFound | Found) =>
        currData
    }
  }

  def checkNotFound(updatedAt: Long, checkInterval: Long): Boolean = {
    val realInterval = getCurrentTime - updatedAt
    realInterval > checkInterval
  }

  def currentInst: HippoInstance = {
   val stateID = stateName.identifier

    stateData match {
      case p: Program =>
        HippoInstance(conf, p.interval, p.updatedAt, None, stateID)
      case p: Process =>
        HippoInstance(conf, p.interval, p.updatedAt, Some(p.monitorPID), stateID)
    }
  }

  def getCurrentCheckInterval(bufferTime: Int = 5) =
    FiniteDuration(stateData.interval + bufferTime, MILLISECONDS)

  /**
    * Finite State Machine
    */

  startWith(Sleep, Program(conf.checkInterval, conf.execTime))

  when(Sleep) {
    case Event(Start(interval), _) =>
      val res = controller.startHippo

      if (res.isSuccess) {
        goto(Running) applying RunSuccess(res.pid.get, interval) andThen { _ =>
          saveStateSnapshot()
        }
      } else {
        goto(Dead) applying RunFail()
      }
  }

  when(Running) {
    case Event(Stop, _) =>
      controller.stopHippo

      cancelTimer(CHECK_TIMER)
      goto(Sleep) applying KillSuccess() andThen { _ =>
        saveStateSnapshot()
      }
    case Event(Restart, _) =>
      val res = controller.restartHippo

      if (res.isSuccess) {
        stay applying RunSuccess(res.pid.get) andThen { _ =>
          saveStateSnapshot()
        }
      } else {
        cancelTimer(CHECK_TIMER)
        goto(Dead) applying RunFail()
      }
    case Event(Report, _) =>
      println(s"Receive Report command at ${getCurrentTime}")
      stay applying Confirm(true)

    case Event(Check, data) =>
      println(s"Receive Check command at ${getCurrentTime}")
      if (checkNotFound(data.updatedAt, data.interval)) {
        cancelTimer(CHECK_TIMER)
        goto(Missing) applying NotFound
      } else {
        stay applying Found
      }
  }

  when(Missing) {
    case Event(Check, _) =>
      val res = controller.checkHippo

      if (res.isSuccess) {
        goto(Running) applying Confirm(true) andThen { _ =>
          saveStateSnapshot()
        }
      } else {
        goto(Dead) applying Confirm(false)
      }
  }

  when(Dead) {
    case Event(Start(_), Program(interval, _, retry)) =>
      println(s"retry: $retry")
      if (retry < conf.maxRetries) {
        val res = controller.startHippo

        if (res.isSuccess) {
          goto(Running) applying RunSuccess(res.pid.get, Some(interval)) andThen { _ =>
            saveStateSnapshot()
          }
        } else {
          goto(Dead) applying RunFail() andThen { _ =>
            saveStateSnapshot()
          }
        }
      } else {
        goto(Sleep) applying GiveUp() andThen { _ =>
          saveStateSnapshot()
        }
      }
  }

  onTransition {
    case _ -> Running =>
      setTimer(CHECK_TIMER, Check, getCurrentCheckInterval(), repeat = true)
    case Running -> Missing =>
      self ! Check
    case _ -> Dead =>
      self ! Start()
  }

  whenUnhandled {
    case Event(GetStatus, _) =>
      stay() replying currentInst

    case Event(PrintStatus, _) =>
      println(currentInst)
      stay()

    case Event(Delete, _) =>
      println("Delete!")
      deleteMessages(lastSequenceNr)
      deleteSnapshot(lastSequenceNr)
      stop()
  }
}
