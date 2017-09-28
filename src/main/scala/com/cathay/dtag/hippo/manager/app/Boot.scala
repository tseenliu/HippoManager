package com.cathay.dtag.hippo.manager.app

import java.io.File

import com.cathay.dtag.hippo.manager.api.APIServer
import com.cathay.dtag.hippo.manager.coord.Coordinator
import com.cathay.dtag.hippo.manager.core.env.EnvLoader
import com.typesafe.config.{Config, ConfigFactory}


object ServiceType {
  val COORDINATOR = "coordinator"
  val API = "api"
}

object Boot extends App with EnvLoader {

  var appType = ServiceType.COORDINATOR

  if (args.length > 0) {
    appType = args(0)
  }

  if (args.length > 1) {
    configDir = args(1)
  }

  appType match {
    case ServiceType.COORDINATOR =>
      val coordConfig = getConfig("coordinator").resolve()
      val reporterConfig = getConfig("reporter")
      Coordinator.initiate(coordConfig, reporterConfig)
    case ServiceType.API =>
      val clusterConfig = getConfig("cluster").resolve()
      val serviceConfig = getConfig("service")
      val server = new APIServer(clusterConfig, serviceConfig)
      server.run
    case _ =>
      print("app type should be 'coordinator' or 'api'")
  }
}
