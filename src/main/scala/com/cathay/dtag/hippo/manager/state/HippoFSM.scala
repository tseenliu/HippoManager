package com.cathay.dtag.hippo.manager.state

import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState

import scala.concurrent.duration._
import HippoFSM._

import scala.reflect.ClassTag
import scala.util.Random
import scala.reflect._


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
  sealed trait HippoData {
    val updatedAt: Long
    val interval: Int
    val retry: Int
  }
  case class Program(interval: Int, updatedAt: Long, retry: Int=0) extends HippoData
  case class Process(monitorPID: Int, interval: Int, updatedAt: Long) extends HippoData {
    override val retry: Int = 0
  }

  // Hippo Domain Event
  def getCurrentTime: Long = System.currentTimeMillis() / 1000

  sealed trait HippoEvent {
    val timestamp: Long = getCurrentTime
  }
  case class RunSuccess(monitorPID: Int, interval: Option[Int]=None) extends HippoEvent
  case object RunFail extends HippoEvent
  case object KillProc extends HippoEvent
  case object ConfirmRunning extends HippoEvent
  case object ConfirmDead extends HippoEvent
  case object NotFound extends HippoEvent
  case object GiveUp extends HippoEvent

  // Command from outer
  sealed trait Command
  case class Start(Interval: Option[Int]=None) extends Command
  case object Stop extends Command
  case object Restart extends Command
  case object Report extends Command // TODO: params about Kafka
  case object Check extends Command

  // SSH result
  case class BashResult(code: Int, pid: Option[Int], echo: String="") {
    def isSuccess: Boolean = code == 0
  }
}


class HippoFSM(conf: HippoConfig) extends PersistentFSM[HippoState, HippoData, HippoEvent]{

  import HippoFSM._

  override def persistenceId: String = conf.key

  override implicit def domainEventClassTag: ClassTag[HippoEvent] = classTag[HippoEvent]

  override def applyEvent(evt: HippoEvent, currData: HippoData): HippoData = {
    evt match {
      case RunSuccess(pid, interval) =>
        Process(pid, currData.interval, evt.timestamp)
      case ConfirmRunning =>
        val proc = currData.asInstanceOf[Process]
        Process(proc.monitorPID, proc.interval, evt.timestamp)
      case (KillProc | ConfirmDead) =>
        Program(currData.interval, evt.timestamp)
      case RunFail =>
        Program(currData.interval, evt.timestamp, currData.retry + 1)
      case GiveUp =>
        Program(currData.interval, evt.timestamp, currData.retry)
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

  /**
    * Finite State Machine
    */

  startWith(Sleep, Program(conf.checkInterval, conf.execTime))

  when(Sleep) {
    case Event(Start(interval), _) =>
      // TODO: sh start.sh
      val res = fakeCallRemote

      if (res.isSuccess) {
        goto(Running) applying RunSuccess(res.pid.get, interval)
      } else {
        goto(Dead) applying RunFail
      }
  }

  when(Running) {
    case Event(Stop, _) =>
      // TODO: sh kill.sh
      goto(Sleep) applying KillProc
    case Event(Restart, _) =>
      // TODO: sh restart.sh
      val res = fakeCallRemote

      if (res.isSuccess) {
        goto(Running) applying RunSuccess(res.pid.get)
      } else {
        goto(Dead) applying RunFail
      }
    case Event(Report, _) =>
      goto(Running) applying ConfirmRunning

    case Event(Check, Process(pid, checkInterval, updatedAt)) =>
      if (checkNotFound(updatedAt, checkInterval)) {
        goto(Missing) applying NotFound
      } else {
        goto(Running) applying ConfirmRunning
      }
  }

  when(Missing) {
    case Event(Check, _) =>
      // TODO: ps aux
      val res = fakeCallRemote
      if (res.isSuccess) {
        goto(Running) applying ConfirmRunning
      } else {
        goto(Dead) applying ConfirmDead
      }
  }

  when(Dead) {
    case Event(Restart, Program(interval, _, retry)) =>
      if (retry < conf.maxRetries) {
        // TODO: sh restart.sh or start.sh
        val res = fakeCallRemote
        if (res.isSuccess) {
          goto(Running) applying RunSuccess(res.pid.get, Some(interval))
        } else {
          goto(Dead) applying RunFail
        }
      } else {
        goto(Sleep) applying GiveUp
      }
  }

  onTransition {
    case _ -> Running =>
      val interval = stateData.asInstanceOf[Process].interval
      setTimer("check_timeout", Check, FiniteDuration(interval, SECONDS), repeat = false)
    case Running -> Missing =>
      self ! Check
    case _ -> Dead =>
      self ! Restart
  }


}
