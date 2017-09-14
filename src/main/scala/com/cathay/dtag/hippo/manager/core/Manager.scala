package com.cathay.dtag.hippo.manager.core

import java.io.File
import java.net.InetAddress

import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import com.cathay.dtag.hippo.manager.conf.{HippoConfig, HippoInstance}
import com.cathay.dtag.hippo.manager.conf.HippoConfig.CoordCommand.{PrintClusterStatus, PrintNodeStatus}
import com.cathay.dtag.hippo.manager.conf.HippoConfig.EntryCommand._
import com.cathay.dtag.hippo.manager.conf.HippoConfig.HippoCommand._
import com.cathay.dtag.hippo.manager.conf.HippoConfig.Response._
import com.typesafe.config.ConfigFactory


object Manager extends App {
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val timeout = Timeout(5 seconds)

  //val configPath = args(0)
  //val reporterConfig = ConfigFactory.parseFile(new File(configPath))
  val reporterConfig = ConfigFactory.parseFile(new File("config/reporter.conf"))
  val coordActor = Coordinator.initiate(2551, reporterConfig)

  coordActor ! PrintClusterStatus
  Thread.sleep(11000)

  //val hConf = HippoConfig("edge1", "batchetl.journey", "/app/journey", checkInterval = 30*1000)
  val hConf = HippoConfig("edge1", "hippos.service.test1", "/Users/Tse-En/Desktop/HippoPlugin/HippoPlugin/test")

  (coordActor ? Register(hConf)) onSuccess {
    case EntryCmdSuccess =>
      println("register successfully")
    case HippoExists =>
      println("HippoExists")
  }
  Thread.sleep(5000)

  (coordActor ? Operation(Start(Some(10000)), hConf.id)) onSuccess {
    case StateCmdSuccess =>
      println("Start success.")
    case StateCmdFailure =>
      println("error, please check state.")
    case HippoNotFound =>
      println("hippo not found.")
    case StateCmdFailure =>
      println("command not handle.")
  }
  Thread.sleep(50000)

  (coordActor ? Operation(Stop, hConf.id)) onSuccess {
    case StateCmdSuccess =>
      println("Stop success.")
    case StateCmdFailure =>
      println("error, please check state.")
    case HippoNotFound =>
      println("hippo not found.")
    case StateCmdFailure =>
      println("command not handle.")
  }
  Thread.sleep(5000)

  (coordActor ? Operation(Start(), hConf.id)) onSuccess {
    case StateCmdSuccess =>
      println("Start success.")
    case StateCmdFailure =>
      println("error, please check state.")
    case HippoNotFound =>
      println("hippo not found.")
    case StateCmdFailure =>
      println("command not handle.")
  }
  Thread.sleep(40000)

  (coordActor ? Operation(Restart(Some(15000)), hConf.id)) onSuccess {
    case StateCmdSuccess =>
      println("Restart success.")
    case StateCmdFailure =>
      println("error, please check state.")
    case HippoNotFound =>
      println("hippo not found.")
    case StateCmdFailure =>
      println("command not handle.")
  }
  Thread.sleep(35000)


  (coordActor ? Operation(GetStatus, hConf.id)) onSuccess {
    case hi: HippoInstance =>
      println(hi)
    case HippoNotFound =>
      println("hippo not found.")
  }
  Thread.sleep(20000)

  coordActor ! PrintNodeStatus
  Thread.sleep(5000)

  coordActor ! PrintClusterStatus



//  coordActor ! Operation(Start(Some(5000)), hConf.id)
//  coordActor ! PrintNodeStatus

//  coordActor ! Operation(Report, hConf.id)
//  coordActor ! PrintNodeStatus

//  coordActor ! Remove(hConf.host, hConf.name)
//  coordActor ! PrintNodeStatus

//  Thread.sleep(10000)
//
//  coordActor ! PrintClusterStatus
}

