package com.cathay.dtag.hippo.manager.state


case class HippoConfig(host: String,
                       name: String,
                       path: String,
                       execTime: Long=HippoConfig.getCurrentTime,
                       maxRetries: Int=3,
                       checkInterval: Int=HippoConfig.DEFAULT_INTERVAL) {

  def key = s"$name@$host:$path"
}

//case class HippoInstance(conf: HippoConfig,
//                         checkInterval: Int=HippoConfig.DEFAULT_INTERVAL,
//                         lastUpdateTime: Long=HippoConfig.getCurrentTime,
//                         monitorPID: Option[Int]=None,
//                         state: String=HippoFSM.Sleep.identifier) {
//
//  override def toString: String = s"${conf.key}, executed at ${conf.execTime}, last updated at $lastUpdateTime"
//}


object HippoConfig {
  val DEFAULT_INTERVAL: Int = 30*1000
  def getCurrentTime: Long = System.currentTimeMillis() / 1000

  // Command from outer
  sealed trait Command
  case class Start(Interval: Option[Int]=None) extends Command
  case object Stop extends Command
  case object Restart extends Command
  case object Report extends Command // TODO: params about Kafka
  case object Check extends Command
  // only for entry
  case class Register(conf: HippoConfig) extends Command
  case class Remove(key: String)

  // SSH result
  case class BashResult(code: Int, pid: Option[Int], echo: String="") {
    def isSuccess: Boolean = code == 0
  }
}

