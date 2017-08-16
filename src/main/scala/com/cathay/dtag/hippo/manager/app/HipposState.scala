package com.cathay.dtag.hippo.manager.app

import java.time.Instant


object HipposState {
  val STATUS_DEAD = "dead"
  val STATUS_RUNNING = "running"

  case class HippoService(host: String, name: String) {
    val registerTime: Long = Instant.now.getEpochSecond
    def key = s"${name}@${host}"
    override def toString: String = s"${this.key} created at $registerTime"
  }

  sealed trait Operation
  case object Awake extends Operation
  case object Kill extends Operation

  case class Evt(hs: HippoService, op: Operation)
  case class Cmd(hs: HippoService, op: Operation)


  case object GetLocalState
  case object GetGlobalState
  case object UpdateStates
}

case class HipposState(state: Map[String, String] = Map()) {
  import HipposState._

  def size: Int = state.size

  def updateHippoStatus(evt: Evt): HipposState = {
    val old_status = state.getOrElse(evt.hs.key, STATUS_DEAD)

    evt match {
      case Evt(hs, Awake) if old_status == STATUS_DEAD =>
        copy(state + (hs.key -> STATUS_RUNNING))
      case Evt(hs, Kill) if old_status == STATUS_RUNNING =>
        copy(state + (hs.key -> STATUS_DEAD))
      case x =>
        println(s"Status and Operation is ambiguous: $x")
        this
    }
  }
}
