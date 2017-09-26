package com.cathay.dtag.hippo.manager.core.schema

case class HippoInstance(conf: HippoConfig,
                         checkInterval: Long,
                         lastUpdateTime: Long,
                         monitorPID: Option[Int],
                         state: String) {

  override def toString: String = {
    val str = s"[${conf.name}@${conf.host}] SERVICE ID: ${conf.id}, CREATED: ${conf.execTime}, UPDATED: $lastUpdateTime, STATE: $state"
    if (monitorPID.isEmpty) {
      str
    } else {
      str + s", PID: ${monitorPID.get}"
    }
  }
}

case class HippoGroup(nodeAddress: String, group: Map[String, HippoInstance] = Map()) {
  val createdAt: Long = HippoConfig.getCurrentTime

  def merge(state: HippoGroup): HippoGroup = {
    HippoGroup(nodeAddress, group ++ state.group)
  }
}
