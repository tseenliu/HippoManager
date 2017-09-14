package com.cathay.dtag.hippo.manager.state

import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import HippoStateActor._
import akka.persistence.{SaveSnapshotFailure, SaveSnapshotSuccess}
import com.cathay.dtag.hippo.manager.conf.HippoConfig.Response.{StateCmdFailure, StateCmdSuccess}
import com.cathay.dtag.hippo.manager.conf.{HippoConfig, HippoInstance}

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
  case class Program(interval: Long, updatedAt: Long, retry: Int=1) extends HippoData
  case class Process(monitorPID: Int, interval: Long, updatedAt: Long) extends HippoData {
    override val retry: Int = 0
  }

  // FSM Event
  sealed trait HippoEvent{
    val timestamp: Long = getCurrentTime
  }
  case class RunSuccess(monitorPID: Int, interval: Option[Long]=None) extends HippoEvent
  case class RunFail(interval: Option[Long]=None) extends HippoEvent
  case class KillSuccess() extends HippoEvent
  case class Confirm(isRunning: Boolean) extends HippoEvent
  case object Found extends HippoEvent
  case object NotFound extends HippoEvent
  case class GiveUp() extends HippoEvent
  case class ReportSuccess(updateAt: Long) extends HippoEvent
}


class HippoStateActor(var conf: HippoConfig) extends PersistentFSM[HippoState, HippoData, HippoEvent] {

  import HippoStateActor._
  import HippoConfig._
  import HippoConfig.HippoCommand._

  val CHECK_TIMER: String = "check_timeout"
  val RETRY_TIMER: String = "retry_timeout"

  // TODO: Start, Restart, Stop and Check command with Hippo Config
  var controller = new CommandController(conf)

  override def persistenceId: String = conf.id

  override def domainEventClassTag: ClassTag[HippoEvent] = classTag[HippoEvent]

  override def applyEvent(evt: HippoEvent, currData: HippoData): HippoData = {
    evt match {
      case RunSuccess(pid, interval) =>
        Process(pid, interval.get, evt.timestamp)
      case Confirm(true) =>
        val proc = currData.asInstanceOf[Process]
        Process(proc.monitorPID, proc.interval, evt.timestamp)
      case ReportSuccess(updateAt) =>
        val proc = currData.asInstanceOf[Process]
        Process(proc.monitorPID, proc.interval, updateAt)
      case (KillSuccess() | Confirm(false)) =>
        Program(currData.interval, evt.timestamp)
      case RunFail(interval) =>
        Program(interval.get, evt.timestamp, currData.retry + 1)
      case GiveUp() =>
        Program(currData.interval, evt.timestamp)
      case (NotFound | Found) =>
        currData
    }
  }

  def checkNotFound(updatedAt: Long, checkInterval: Long): Boolean = {
    val ct = getCurrentTime
    val realInterval = ct - updatedAt
//    println(s"ct: $ct")
//    println(s"ut: $updatedAt")
//    println(s"real: $realInterval")
//    println(s"expect: $checkInterval")
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

  def getCurrentCheckInterval(bufferTime: Int = 1200) =
    FiniteDuration(stateData.interval + bufferTime, MILLISECONDS)

  /**
    * Finite State Machine
    */
  startWith(Sleep, Program(HippoConfig.DEFAULT_INTERVAL, conf.execTime))

  when(Sleep) {
    case Event(Start(interval), _) =>
      val checkInterval = interval.getOrElse(HippoConfig.DEFAULT_INTERVAL)
      val res = controller.startHippo(checkInterval)

      if (res.isSuccess) {
        goto(Running) applying RunSuccess(res.pid.get, Some(checkInterval)) andThen { _ =>
          setTimer(CHECK_TIMER, ReportCheck, getCurrentCheckInterval(), repeat = true)
          saveStateSnapshot()
          sender() ! StateCmdSuccess
        }
      } else {
        goto(Dead) applying RunFail(Some(checkInterval))
      }
  }

  when(Running) {
    case Event(Stop, _) =>
      controller.stopHippo

      cancelTimer(CHECK_TIMER)
      goto(Sleep) applying KillSuccess() andThen { _ =>
        saveStateSnapshot()
        sender() ! StateCmdSuccess
      }
    case Event(Restart(interval), _) =>
      val checkInterval = interval.getOrElse(HippoConfig.DEFAULT_INTERVAL)
      val res = controller.restartHippo(checkInterval)

      if (res.isSuccess) {
        stay applying RunSuccess(res.pid.get, Some(checkInterval)) andThen { _ =>
          saveStateSnapshot()
          sender() ! StateCmdSuccess
        }
      } else {
        cancelTimer(CHECK_TIMER)
        goto(Dead) applying RunFail()
      }
    case Event(Report(updatedAt), _) =>
      //println(s"Receive Report command at $updatedAt")
      stay applying ReportSuccess(updatedAt)

    case Event(ReportCheck, _) =>
      //println(s"Receive Check command at ${getCurrentTime}")
      if (checkNotFound(stateData.updatedAt, stateData.interval)) {
        cancelTimer(CHECK_TIMER)
        goto(Missing) applying NotFound andThen { _ =>
          saveStateSnapshot()
        }
      } else {
        println(s"${conf.name}@${conf.host}] checking successfully.")
        stay applying Found
      }
  }

  when(Missing) {
    case Event(Check, _) =>
      val res = controller.checkHippo

      if (res.isSuccess) {
        cancelTimer(CHECK_TIMER)
        goto(Running) applying Confirm(true) andThen { _ =>
          saveStateSnapshot()
        }
      } else {
        goto(Dead) applying Confirm(false)
      }
  }

  when(Dead) {
    case Event(Retry, Program(interval, _, retry)) =>
      if (retry <= conf.maxRetries) {
        println(s"retry: $retry")
        val res = controller.startHippo(interval)

        if (res.isSuccess) {
          cancelTimer(RETRY_TIMER)
          goto(Running) applying RunSuccess(res.pid.get, Some(interval)) andThen { _ =>
            saveStateSnapshot()
            sender() ! StateCmdSuccess
          }
        } else {
          stay applying RunFail(Some(interval)) andThen { _ =>
            self ! Retry
          }
        }
      } else {
        cancelTimer(RETRY_TIMER)
        stay applying GiveUp() andThen { _ =>
          saveStateSnapshot()
          sender() ! StateCmdFailure
        }
      }
    case Event(Start(interval), _) =>
      goto(Sleep) applying Found andThen { _ =>
        self ! Start(interval)
      }
  }

  onTransition {
    case (Missing | Dead | Running) -> Running =>
      setTimer(CHECK_TIMER, ReportCheck, getCurrentCheckInterval(), repeat = true)
    case _ -> Missing =>
      self ! Check
    case (Sleep | Running | Missing) -> Dead =>
      setTimer(RETRY_TIMER, Retry, 3 seconds, repeat = true)
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

    case Event(SaveSnapshotSuccess(metadata), _) ⇒
      //println(s"state shifted and save snapshot succeed.")
      stay()

    case Event(SaveSnapshotFailure(metadata, reason), _) ⇒
      println(s"save snapshot failed and failure is ${reason}")
      stay()

    case Event(msg, _) =>
      println(s"[ERROR] Wrong $msg command in: $currentInst")
      sender() ! StateCmdFailure
      stay()
  }
}
