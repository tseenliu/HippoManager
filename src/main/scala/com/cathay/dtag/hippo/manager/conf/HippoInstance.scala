package com.cathay.dtag.hippo.manager.conf


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

case class HippoGroup(group: Map[String, HippoInstance] = Map()) {
  val createdAt: Long = HippoConfig.getCurrentTime

  def merge(state: HippoGroup): HippoGroup = {
    HippoGroup(group ++ state.group)
  }
}
