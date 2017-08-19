package com.cathay.dtag.hippo.manager.state


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

case class HippoInstance(conf: HippoConfig,
                         checkInterval: Long,
                         lastUpdateTime: Long,
                         monitorPID: Option[Int],
                         state: String) {

  override def toString: String = {
    val str = s"${conf.id}, executed at ${conf.execTime}, last updated at $lastUpdateTime, now is $state"
    if (monitorPID.isEmpty) {
      str
    } else {
      str + s", run with pid ${monitorPID.get}"
    }
  }
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
  sealed trait Command
  object Command {
    case class Start(Interval: Option[Long]=None) extends Command
    case object Stop extends Command
    case object Restart extends Command
    case object Report extends Command // TODO: params about Kafka
    case object Check extends Command
    case object GetStatus extends Command
    case object PrintStatus extends Command
    case object Delete extends Command

    // only for entry
    case class Register(conf: HippoConfig) extends Command
    case class Remove(host: String, name: String) extends Command
  }
}

