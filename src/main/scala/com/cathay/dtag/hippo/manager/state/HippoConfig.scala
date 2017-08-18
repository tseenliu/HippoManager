package com.cathay.dtag.hippo.manager.state


case class HippoConfig(host: String,
                       name: String,
                       path: String,
                       execTime: Long=HippoConfig.getCurrentTime,
                       maxRetries: Int=3,
                       checkInterval: Int=HippoConfig.DEFAULT_INTERVAL) {

  def location: String = s"$name@$host:$path"
  def id: String = HippoConfig.hash(s"$name@$host")
}

case class HippoInstance(conf: HippoConfig,
                         checkInterval: Int,
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
  val DEFAULT_INTERVAL: Int = 30*1000
  def getCurrentTime: Long = System.currentTimeMillis() / 1000

  def hash(s: String): String = {
    val m = java.security.MessageDigest.getInstance("MD5")
    val b = s.getBytes("UTF-8")
    m.update(b, 0, b.length)
    new java.math.BigInteger(1, m.digest()).toString(16)
  }

  // Command from outer
  sealed trait Command
  object Command {
    case class Start(Interval: Option[Int]=None) extends Command
    case object Stop extends Command
    case object Restart extends Command
    case object Report extends Command // TODO: params about Kafka
    case object Check extends Command
    case object GetStatus extends Command
    case object PrintStatus extends Command
    case object Delete extends Command

    // only for entry
    case class Register(conf: HippoConfig) extends Command
    case class Remove(key: String) extends Command
  }

  // SSH result
  case class BashResult(code: Int, pid: Option[Int], echo: String="") {
    def isSuccess: Boolean = code == 0
  }
}

