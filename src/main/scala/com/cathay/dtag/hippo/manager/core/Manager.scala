package com.cathay.dtag.hippo.manager.core

import java.net.InetAddress

import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

import com.cathay.dtag.hippo.manager.conf.HippoConfig
import com.cathay.dtag.hippo.manager.conf.HippoConfig.CoordCommand.{PrintClusterStatus, PrintNodeStatus}
import com.cathay.dtag.hippo.manager.conf.HippoConfig.EntryCommand._
import com.cathay.dtag.hippo.manager.conf.HippoConfig.HippoCommand._
import com.cathay.dtag.hippo.manager.conf.HippoConfig.Response.{EntryCmdSuccess, HippoExists}


object Manager extends App {
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val timeout = Timeout(5 seconds)

  val coordActor = Coordinator.initiate(2551)

  coordActor ! PrintClusterStatus

  val hConf = HippoConfig("edge1", "batchetl.journey", "/app/journey", checkInterval = 30*1000)

  coordActor ! PrintNodeStatus
  (coordActor ? Register(hConf)) onSuccess {
    case EntryCmdSuccess =>
      println("register successfully")
    case HippoExists =>
      println("HippoExists")
  }

  Thread.sleep(500)

  coordActor ! Operation(Start(), hConf.id)
  coordActor ! PrintNodeStatus

  coordActor ! Operation(Report, hConf.id)
  coordActor ! PrintNodeStatus

//  coordActor ! Remove(hConf.host, hConf.name)
//  coordActor ! PrintNodeStatus

  Thread.sleep(1000)

  coordActor ! PrintClusterStatus
}

