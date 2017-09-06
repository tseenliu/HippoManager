package com.cathay.dtag.hippo.manager.conf


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

case class HippoGroup(nodeAddress: String, group: Map[String, HippoInstance] = Map()) {
  val createdAt: Long = HippoConfig.getCurrentTime

  def merge(state: HippoGroup): HippoGroup = {
    HippoGroup(nodeAddress, group ++ state.group)
  }
}
