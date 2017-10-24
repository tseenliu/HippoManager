package com.cathay.dtag.hippo.manager.core.schema

case class HippoInstance(conf: HippoConfig,
                         checkInterval: Long,
                         lastUpdateTime: Long,
                         monitorPID: Option[Int],
                         state: String) {

  override def toString: String = {
    val str = s"[${conf.name}@${conf.clientIP}] SERVICE ID: ${conf.id}, CREATED: ${conf.execTime}, UPDATED: $lastUpdateTime, STATE: $state"
    if (monitorPID.isEmpty) {
      str
    } else {
      str + s", PID: ${monitorPID.get}"
    }
  }

  def isMatch(params: Map[String, String]): Boolean = {
    List("user", "clientIP", "serviceName")
      .filter(f => params contains f)
      .forall {
        case "user" =>
          conf.user == params("user")
        case "clientIP" =>
          conf.clientIP == params("clientIP")
        case "serviceName" =>
          conf.name == params("serviceName")
        case _ =>
          true
      }
  }
}

case class HippoGroup(nodeAddress: String, group: Map[String, HippoInstance] = Map()) {
  val createdAt: Long = HippoConfig.getCurrentTime

  def merge(state: HippoGroup): HippoGroup = {
    HippoGroup(nodeAddress, group ++ state.group)
  }

  def filterByParams(params: Map[String, String]): HippoGroup = {
    val filterGroup = group.filter(_._2.isMatch(params))
    HippoGroup(nodeAddress, filterGroup)
  }
}
