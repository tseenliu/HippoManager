package com.cathay.dtag.hippo.manager.state


case class HippoConfig(host: String,
                       name: String,
                       path: String,
                       execTime: Long=HippoService.getCurrentTime) {
  def key = s"${name}@${host}:${path}"
}

case class HippoService(conf: HippoConfig,
                        checkInterval: Int=HippoService.DEFAULT_INTERVAL,
                        lastUpdateTime: Long=HippoService.getCurrentTime,
                        monitorPID: Option[Int]=None,
                        state: HippoService.State=HippoService.Sleep) {

  override def toString: String = s"${conf.key}, executed at ${conf.execTime}, last updated at $lastUpdateTime"
}


object HippoService {
  val DEFAULT_INTERVAL: Int = 30*1000
  def getCurrentTime: Long = System.currentTimeMillis() / 1000

  // FSM State
  sealed trait State
  case object Sleep extends State
  case object Running extends State
  case object Missing extends State
  case object Dead extends State

  // FSM Data
  sealed trait Data {
    val updatedAt: Long = getCurrentTime
  }
  case object IdleHippo extends Data
  case class ActiveHippo(monitorPID: Int) extends Data

  // Event
  trait Action
  case class Run(monitorPID: Int) extends Action
  case object Kill extends Action
  case class Update(monitorPID: Option[Int]=None) extends Action
  case object NotFound extends Action
  case object ConfirmDead extends Action
  case object GiveUp extends Action

  case class Operation(hs: HippoService, act: Action)
  case class Register(conf: HippoConfig)

  case object GetState
}

