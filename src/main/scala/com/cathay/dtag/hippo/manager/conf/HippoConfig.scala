package com.cathay.dtag.hippo.manager.conf

case class HippoConfig(host: String,
                       name: String,
                       path: String,
                       execTime: Long=HippoConfig.getCurrentTime,
                       maxRetries: Int=3,
                       checkInterval: Long=HippoConfig.DEFAULT_INTERVAL) {

  def location: String = s"$name@$host:$path"
  val key: String = s"$name@$host"
  val id: String = HippoConfig.generateHippoID(host, name)
  override val hashCode: Int = HippoConfig.hash(key).intValue()
}

object HippoConfig {
  val DEFAULT_INTERVAL: Long = 30 * 1000 // 30 seconds
  def getCurrentTime: Long = System.currentTimeMillis()

  def hash(s: String): BigInt = {
    val m = java.security.MessageDigest.getInstance("MD5")
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
    case class Start(Interval: Option[Long]=None) extends HippoCommand
    case object Stop extends HippoCommand
    case object Restart extends HippoCommand
    case object Report extends HippoCommand // TODO: params about Kafka
    case object Check extends HippoCommand
    case object GetStatus extends HippoCommand
    case object PrintStatus extends HippoCommand
    case object Delete extends HippoCommand
  }

  sealed trait EntryCommand extends ManagerCommand
  object EntryCommand {
    case class Register(conf: HippoConfig) extends EntryCommand
    case class Remove(host: String, name: String) extends EntryCommand
    case class Operation(cmd: HippoCommand, id: String) extends EntryCommand
    case object GetNodeStatus extends EntryCommand
  }

  sealed trait CoordCommand extends ManagerCommand
  object CoordCommand {
    case object UpdateStatus extends CoordCommand
    case object PrintNodeStatus extends CoordCommand
    case object PrintClusterStatus extends CoordCommand
  }

  // Response
  sealed trait Response
  object Response {
    case object HippoExists extends Response
    case object HippoNotFound extends Response
    case object EntryCmdSuccess extends Response
  }
}

