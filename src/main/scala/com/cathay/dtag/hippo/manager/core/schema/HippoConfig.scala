package com.cathay.dtag.hippo.manager.core.schema

import java.security.MessageDigest


case class HippoConfig(clientIP: String,
                       name: String,
                       path: String,
                       user: String="UNKNOWN",
                       execTime: Long=HippoConfig.getCurrentTime,
                       maxRetries: Int=3) {

  def location: String = s"$name@$clientIP:$path"
  val key: String = s"$name@$clientIP"
  val id: String = HippoConfig.generateHippoID(clientIP, name)
  override val hashCode: Int = HippoConfig.hash(key).intValue()
}

object HippoConfig {
  val DEFAULT_INTERVAL: Long = 30 * 1000 // 30 seconds
  val CHECK_BUFFER_QUEUE_SIZE: Int = 5
  val CHECK_BUFFER_TIME: Long = 3000
  def getCurrentTime: Long = System.currentTimeMillis()

  def hash(s: String): BigInt = {
    val m = MessageDigest.getInstance("MD5")
    val b = s.getBytes("UTF-8")
    m.update(b, 0, b.length)
    new java.math.BigInteger(1, m.digest())//.toString(16)
  }

  def generateHippoID(host: String, name: String): String = {
    hash(s"$name@$host").toString(16)
  }

  // Command from outer
  trait ManagerCommand

  sealed trait HippoCommand extends ManagerCommand
  object HippoCommand {
    case class Start(interval: Option[Long]=None) extends HippoCommand
    case object Stop extends HippoCommand
    case class Restart(Interval: Option[Long]=None) extends HippoCommand
    case class Report(updatedAt: Long) extends HippoCommand
    case object CheckRemote extends HippoCommand
    case object GetStatus extends HippoCommand
    case object PrintStatus extends HippoCommand
    case object Delete extends HippoCommand
    case object Retry extends HippoCommand
    case class Revive(monitorPID: Int, interval: Option[Long]=None) extends HippoCommand
  }

  sealed trait EntryCommand extends ManagerCommand
  object EntryCommand {
    case class Register(conf: HippoConfig) extends EntryCommand
    case class Remove(id: String) extends EntryCommand
    case class Operation(cmd: HippoCommand, id: String) extends EntryCommand
    case class GetNodeStatus(params: Map[String, String]=Map()) extends EntryCommand
  }

  sealed trait CoordCommand extends ManagerCommand
  object CoordCommand {
    case object UpdateStatus extends CoordCommand
    case object PrintNodeStatus extends CoordCommand
    case object GetClusterStatus extends CoordCommand
  }

  // Response
  sealed trait Response
  object Response {
    case object HippoExists extends Response
    case object HippoNotFound extends Response
    case object EntryCmdSuccess extends Response
    case object StateCmdSuccess extends Response
    case class StateCmdException(reason: String) extends Response
    case class StateCmdUnhandled(currentState: String) extends Response
  }
}

