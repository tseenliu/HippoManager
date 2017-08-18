package com.cathay.dtag.hippo.manager.state

import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState

import scala.concurrent.duration._
import HippoFSM._

import scala.reflect.ClassTag
import scala.util.Random
import scala.reflect._


object HippoFSM {
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
    val interval: Int
    val retry: Int

  }
  case class Program(interval: Int, updatedAt: Long, retry: Int=0) extends HippoData
  case class Process(monitorPID: Int, interval: Int, updatedAt: Long) extends HippoData {
    override val retry: Int = 0
  }

  sealed trait HippoEvent{
    val timestamp: Long = getCurrentTime
  }
  case class RunSuccess(monitorPID: Int, interval: Option[Int]=None) extends HippoEvent
  case class RunFail() extends HippoEvent
  case class KillSuccess() extends HippoEvent
  case class Confirm(isRunning: Boolean) extends HippoEvent
  case class NotFound() extends HippoEvent
  case class GiveUp() extends HippoEvent
}


class HippoFSM(conf: HippoConfig) extends PersistentFSM[HippoState, HippoData, HippoEvent] {

  import HippoFSM._
  import HippoConfig._
  import HippoConfig.Command._

  val CHECK_TIMER: String = "check_timeout"

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
      case NotFound() =>
        currData
    }
  }

  // TODO: call remote
  def fakeCallRemote: BashResult = {
    val code = new Random().nextInt(2)
    val pid = new Random().nextInt(65536)
    val echo = if (code == 0) "stdout" else "stderr"
    BashResult(code, Some(pid), echo)
  }

  def checkNotFound(updatedAt: Long, checkInterval: Int): Boolean = {
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

  /**
    * Finite State Machine
    */

  startWith(Sleep, Program(conf.checkInterval, conf.execTime))

  when(Sleep) {
    case Event(Start(interval), _) =>
      // TODO: sh start.sh
      val res = fakeCallRemote

      //if (res.isSuccess) {
      if (true) {
        goto(Running) applying RunSuccess(res.pid.get, interval) andThen {
          case _ => saveStateSnapshot()
        }
      } else {
        goto(Dead) applying RunFail() andThen {
          case _ => saveStateSnapshot()
        }
      }
  }

  when(Running) {
    case Event(Stop, _) =>
      // TODO: sh kill.sh
      goto(Sleep) applying KillSuccess() andThen {
        case _ => saveStateSnapshot()
      }
    case Event(Restart, _) =>
      // TODO: sh restart.sh
      val res = fakeCallRemote

      if (res.isSuccess) {
        goto(Running) applying RunSuccess(res.pid.get)
      } else {
        goto(Dead) applying RunFail() andThen {
          case _ => saveStateSnapshot()
        }
      }
    case Event(Report, _) =>
      goto(Running) applying Confirm(true)

    case Event(Check, Process(_, checkInterval, updatedAt)) =>
      if (checkNotFound(updatedAt, checkInterval)) {
        goto(Missing) applying NotFound()
      } else {
        goto(Running) applying Confirm(true)
      }
  }

  when(Missing) {
    case Event(Check, _) =>
      // TODO: ps aux
      val res = fakeCallRemote
      if (res.isSuccess) {
        goto(Running) applying Confirm(true)
      } else {
        goto(Dead) applying Confirm(false)
      }
  }

  when(Dead) {
    case Event(Start(_), Program(interval, _, retry)) =>
      println(s"retry: $retry")
      if (retry < conf.maxRetries) {
        // TODO: sh restart.sh or start.sh
        val res = fakeCallRemote

        if (res.isSuccess) {
          goto(Running) applying RunSuccess(res.pid.get, Some(interval)) andThen {
            case _ => saveStateSnapshot()
          }
        } else {
          goto(Dead) applying RunFail() andThen {
            case _ => saveStateSnapshot()
          }
        }
      } else {
        goto(Sleep) applying GiveUp() andThen {
          case _ => saveStateSnapshot()
        }
      }
  }

  onTransition {
    case _ -> Running =>
      setTimer(CHECK_TIMER, Check, FiniteDuration(stateData.interval, SECONDS), repeat = false)
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
      deleteMessages(lastSequenceNr)
      deleteSnapshot(lastSequenceNr)
      stop()
  }
}
