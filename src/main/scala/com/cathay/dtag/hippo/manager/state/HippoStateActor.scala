package com.cathay.dtag.hippo.manager.state

import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import HippoStateActor._
import akka.persistence.{SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotSelectionCriteria}
import com.cathay.dtag.hippo.manager.core.schema.HippoConfig.Response.{StateCmdException, StateCmdSuccess, StateCmdUnhandled}
import com.cathay.dtag.hippo.manager.core.schema.{HippoConfig, HippoInstance}

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
  case object Retrying extends HippoState {
    override def identifier: String = "Retrying"
  }
  case object Dead extends HippoState {
    override def identifier: String = "Dead"
  }

  // FSM Data
  sealed trait HippoData {
    val updatedAt: Long
    val interval: Long
    val retryNum: Int

  }
  case class Program(interval: Long, updatedAt: Long, retryNum: Int=0) extends HippoData
  case class Process(monitorPID: Int, interval: Long, updatedAt: Long) extends HippoData {
    override val retryNum: Int = 0
  }

  // FSM Event
  sealed trait HippoEvent{
    val timestamp: Long = getCurrentTime
  }
  case class RunSuccess(monitorPID: Int, interval: Long) extends HippoEvent
  case class RunFail(interval: Long) extends HippoEvent
  case class KillSuccess() extends HippoEvent
  case class Confirm(isRunning: Boolean) extends HippoEvent
  case object NotFound extends HippoEvent
  case class GiveUp() extends HippoEvent
  case class ReportSuccess(updateAt: Long) extends HippoEvent

  // Interval Prediction
  case class ReportTime(last: Long = HippoConfig.getCurrentTime,
                        current: Long = HippoConfig.getCurrentTime)

  case class IntervalQueue(iq: List[Long] = List(),
                           max_size: Int=HippoConfig.CHECK_BUFFER_QUEUE_SIZE) {

    def getInterval(percent: Double=0.75): Long = {
      println(s"Queue: ${iq.sorted}")
      iq.length match {
        case v if v == max_size =>
          val index = (max_size * percent).toInt
          iq.sortWith(_ < _)(index)
        case _ => -1
      }
    }

    def addInterval(interval: Long): IntervalQueue = {
      if (iq.length == max_size) {
        IntervalQueue(iq.tail :+ interval, max_size)
      } else {
        IntervalQueue(iq :+ interval, max_size)
      }
    }
  }

}


class HippoStateActor(var conf: HippoConfig,
                      coordAddress: String,
                      defaultInterval: Long,
                      checkBufferTime: Long,
                      checkQueueSize: Int
                     ) extends PersistentFSM[HippoState, HippoData, HippoEvent] {

  import HippoStateActor._
  import HippoConfig.HippoCommand._

  var controller = new CommandController(conf)
  var reportTime = ReportTime()
  var intervalQueue = IntervalQueue(max_size = checkQueueSize)

  override def persistenceId: String = conf.id

  override def domainEventClassTag: ClassTag[HippoEvent] = classTag[HippoEvent]

  override def applyEvent(evt: HippoEvent, currData: HippoData): HippoData = {
    evt match {
      case RunSuccess(pid, interval) =>
        Process(pid, interval, evt.timestamp)
      case Confirm(true) =>
        val proc = currData.asInstanceOf[Process]
        Process(proc.monitorPID, proc.interval, evt.timestamp)
      case Confirm(false) =>
        Program(currData.interval, currData.updatedAt)
      case ReportSuccess(updateAt) =>
        val proc = currData.asInstanceOf[Process]
        val lastUpdatedAt = currData.updatedAt max updateAt
        Process(proc.monitorPID, proc.interval, lastUpdatedAt)
      case KillSuccess() =>
        Program(currData.interval, evt.timestamp)
      case RunFail(interval) =>
        Program(interval, evt.timestamp, currData.retryNum + 1)
      case GiveUp() =>
        Program(currData.interval, evt.timestamp)
      case NotFound =>
        currData
    }
  }

  /**
    * Get current instance info
    * @return HippoInstance
    */
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
    * Check Remote Setting
    */
  val CHECK_TIMER: String = "check_timeout"

  def setCheckTimer(): Unit = {
    val currentInterval = getCheckoutInterval
    println("Timer Interval:", currentInterval)
    val time = currentInterval + checkBufferTime //HippoConfig.CHECK_BUFFER_TIME
    val timeoutDuration = FiniteDuration(time, MILLISECONDS)
    setTimer(CHECK_TIMER, CheckRemote, timeoutDuration)
  }

  def reset(): Unit = {
    intervalQueue = IntervalQueue(max_size = checkQueueSize)
    reportTime = ReportTime()
  }

  def getCheckoutInterval: Long = {
    intervalQueue.getInterval() match {
      case -1 => stateData.interval
      case x => x
    }
  }

  def updateCheckoutInterval(updateAt: Long) = {
    // Update new real Interval
    reportTime = ReportTime(reportTime.current, updateAt)
    // Add to Queue List
    val interval = reportTime.current - reportTime.last
    intervalQueue = intervalQueue.addInterval(interval)
//    if (intervalQueue.iq.length == HippoConfig.CHECK_BUFFER_QUEUE_SIZE) {
//      intervalQueue = IntervalQueue(intervalQueue.iq.tail :+ interval)
//    } else {
//      intervalQueue = IntervalQueue(intervalQueue.iq :+ interval)
//    }
  }

  /**
    * Delete all the hippo related persistent data
    */
  def clearPersistentData(): Unit = {
    (1L to lastSequenceNr) foreach deleteMessages
    deleteSnapshots(SnapshotSelectionCriteria(maxSequenceNr = lastSequenceNr))
  }

  /**
    * Finite State Machine
    */
  startWith(Sleep, Program(defaultInterval, conf.execTime))

  when(Sleep) {
    case Event(Start(interval), _) =>
      val checkInterval = interval.getOrElse(defaultInterval)
      val res = controller.startHippo(checkInterval)

      if (res.isSuccess) {
        goto(Running) applying RunSuccess(res.pid.get, checkInterval) andThen { _ =>
          saveStateSnapshot()
          sender() ! StateCmdSuccess
        }
      } else {
        goto(Dead) applying RunFail(checkInterval) andThen { _ =>
          saveStateSnapshot()
          sender() ! StateCmdException(res.echo)
        }
      }
    case Event(Revive(pid, interval), _) =>
      val checkInterval = interval.getOrElse(defaultInterval)
      goto(Running) applying RunSuccess(pid, checkInterval) andThen { _ =>
        saveStateSnapshot()
      }
    case Event(Delete, _) =>
      clearPersistentData()
      sender() ! StateCmdSuccess
      stop()
  }

  when(Running) {
    case Event(Stop, _) =>
      cancelTimer(CHECK_TIMER)
      controller.stopHippo

      goto(Sleep) applying KillSuccess() andThen { _ =>
        saveStateSnapshot()
        sender() ! StateCmdSuccess
      }
    case Event(Restart(interval), _) =>
      cancelTimer(CHECK_TIMER)
      val checkInterval = interval.getOrElse(defaultInterval)
      val res = controller.restartHippo(checkInterval)

      if (res.isSuccess) {
        stay applying RunSuccess(res.pid.get, checkInterval) andThen { _ =>
          reset()
          setCheckTimer()
          sender() ! StateCmdSuccess
        }
      } else {
        goto(Dead) applying RunFail(checkInterval) andThen { _ =>
          saveStateSnapshot()
          sender() ! StateCmdException(res.echo)
        }
      }
    case Event(Report(updatedAt), _) =>
      println(s"Report Successfully")
      cancelTimer(CHECK_TIMER)
      updateCheckoutInterval(updatedAt)
      stay applying ReportSuccess(updatedAt) andThen { _ =>
        setCheckTimer()
      }
    case Event(CheckRemote, _) =>
      println(s"${conf.name}@${conf.host}] report timeout.")
      println(s"Check Remote.")
      cancelTimer(CHECK_TIMER)

      val res = controller.checkHippo
      if (res.isSuccess) {
        stay applying Confirm(true) andThen { _ =>
          setCheckTimer()
        }
      } else {
        goto(Retrying) applying Confirm(false)
      }
  }

  when(Retrying, stateTimeout = 10 seconds) {
    case Event(Retry, Program(interval, _, retryNum)) =>
      if (retryNum < conf.maxRetries) {
        println(s"retry num: $retryNum")
        val res = controller.startHippo(interval)

        if (res.isSuccess) {
          goto(Running) applying RunSuccess(res.pid.get, interval) andThen { _ =>
            saveStateSnapshot()
          }
        } else {
          stay applying RunFail(interval) andThen { _ =>
            self ! Retry
          }
        }
      } else {
        goto(Dead) applying GiveUp() andThen { _ =>
          saveStateSnapshot()
        }
      }
    case Event(StateTimeout, _) =>
      stay andThen { _ =>
        self ! Retry
      }
  }

  when(Dead) {
    case Event(Start(interval), _) =>
      val checkInterval = interval.getOrElse(defaultInterval)
      val res = controller.startHippo(checkInterval)

      if (res.isSuccess) {
        goto(Running) applying RunSuccess(res.pid.get, checkInterval) andThen { _ =>
          saveStateSnapshot()
          sender() ! StateCmdSuccess
        }
      } else {
        stay applying RunFail(checkInterval) andThen { _ =>
          sender() ! StateCmdException(res.echo)
        }
      }

    case Event(Delete, _) =>
      clearPersistentData()
      sender() ! StateCmdSuccess
      stop()
  }

  onTransition {
    case (Sleep | Retrying | Dead) -> Running =>
      reset()
      setCheckTimer()
    case _ -> Retrying =>
      self ! Retry
  }

  whenUnhandled {
    case Event(GetStatus, _) =>
      stay() replying currentInst

    case Event(PrintStatus, _) =>
      println(currentInst)
      stay()

    case Event(SaveSnapshotSuccess(metadata), _) ⇒
      stay()

    case Event(SaveSnapshotFailure(metadata, reason), _) ⇒
      println(s"save snapshot failed and failure is $reason")
      stay()

    case Event(msg, _) =>
      sender() ! StateCmdUnhandled(stateName.identifier)
      stay()
  }
}
