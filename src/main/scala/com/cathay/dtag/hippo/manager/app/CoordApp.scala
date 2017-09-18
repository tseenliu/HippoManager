package com.cathay.dtag.hippo.manager.app

import java.io.File

import akka.pattern.ask
import akka.util.Timeout
import com.cathay.dtag.hippo.manager.conf.HippoConfig.CoordCommand._
import com.cathay.dtag.hippo.manager.conf.HippoConfig.EntryCommand._
import com.cathay.dtag.hippo.manager.conf.HippoConfig.HippoCommand._
import com.cathay.dtag.hippo.manager.conf.HippoConfig.Response._
import com.cathay.dtag.hippo.manager.conf.{HippoConfig, HippoInstance}
import com.cathay.dtag.hippo.manager.core.Coordinator
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._


object CoordApp extends App {
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val timeout = Timeout(5 seconds)

  //val configPath = args(0)
  //val reporterConfig = ConfigFactory.parseFile(new File(configPath))
  val reporterConfig = ConfigFactory.parseFile(new File("config/reporter.conf"))
  val coordActor = Coordinator.initiate(2551, reporterConfig)

  Thread.sleep(11000)

  //val hConf = HippoConfig("edge1", "batchetl.journey", "/app/journey", checkInterval = 30*1000)
  val hConf1 = HippoConfig("Tse-EndeMacBook-Pro.local", "recommender-prediction", "/Users/Tse-En/Desktop/happy/HippoPlugin/test/recommender_system")
  val hConf2 = HippoConfig("Tse-EndeMacBook-Pro.local", "recommender-training", "/Users/Tse-En/Desktop/happy/HippoPlugin/test/recommender_system")
  val hConf3 = HippoConfig("Tse-EndeMacBook-Pro.local", "recommender-evaluation", "/Users/Tse-En/Desktop/happy/HippoPlugin/test/recommender_system")
  val list: List[HippoConfig] = List(hConf1, hConf2, hConf3)
  val hippoMap = Map("recommender-prediction" -> hConf1, "recommender-training" -> hConf2, "recommender-evaluation" -> hConf3)

  println("u can type hippo command: ")
  var ok = true
  while (ok) {
    val cmd = Console.readLine()
    ok = cmd != null

    cmd match {
      case msg if msg.startsWith("hippo delete") => coordActor ! Operation(Delete, hippoMap(msg.substring(13)).id)
      case msg if msg.startsWith("hippo register") => register()
      case msg if msg.startsWith("hippo show") =>
        val serviceName = msg.substring(11)
        println(serviceName)
        showStatus(serviceName)
      case msg if msg.startsWith("hippo nodestatus") => coordActor ! PrintNodeStatus

      case msg if msg.startsWith("hippo start") =>
        val tmp = msg.substring(12).split(" ")
        if (tmp.length == 1) {
          val serviceName = tmp(0)
          defaultStart(serviceName)
        } else if(tmp.length == 2) {
          val serviceName = tmp(0)
          val interval = tmp(1).toLong
          start(serviceName, interval)
        }

      case msg if msg.startsWith("hippo stop") =>
        val serviceName = msg.substring(11)
        stop(serviceName)

      case msg if msg.startsWith("hippo restart") =>
        val tmp = msg.substring(14).split(" ")
        if (tmp.length == 1) {
          val serviceName = tmp(0)
          defaultRestart(serviceName)
        } else if(tmp.length == 2) {
          val serviceName = tmp(0)
          val interval = tmp(1).toLong
          restart(serviceName, interval)
        }
      case _ => println("hippo command not found.")
    }


    def register() = {
      for (i <- 0 to 2 ) {
        (coordActor ? Register(list(i))) onSuccess {
          case EntryCmdSuccess =>
            println("register successfully")
          case HippoExists =>
            println("HippoExists")
        }
      }
    }

    def showStatus(serviceName: String) = {
      (coordActor ? Operation(GetStatus, hippoMap(serviceName).id)) onSuccess {
        case hi: HippoInstance =>
          println(hi)
        case HippoNotFound =>
          println("hippo not found.")
      }
    }

    def defaultStart(serviceName: String) = {
      (coordActor ? Operation(Start(), hippoMap(serviceName).id)) onSuccess {
        case StateCmdSuccess =>
          println(s"Start success, interval default 30 sec.")
        case StateCmdFailure =>
          println("error, please check state.")
        case HippoNotFound =>
          println("hippo not found.")
        case StateCmdFailure =>
          println("command not handle.")
      }
    }

    def start(serviceName: String, interval: Long) = {
      val msInterval = interval * 1000
      (coordActor ? Operation(Start(Some(msInterval)), hippoMap(serviceName).id)) onSuccess {
        case StateCmdSuccess =>
          println(s"Start success, interval $interval sec.")
        case StateCmdFailure =>
          println("error, please check state.")
        case HippoNotFound =>
          println("hippo not found.")
        case StateCmdFailure =>
          println("command not handle.")
      }
    }

    def stop(serviceName: String) = {
      (coordActor ? Operation(Stop, hippoMap(serviceName).id)) onSuccess {
        case StateCmdSuccess =>
          println("Stop success.")
        case StateCmdFailure =>
          println("error, please check state.")
        case HippoNotFound =>
          println("hippo not found.")
        case StateCmdFailure =>
          println("command not handle.")
      }
    }

    def restart(serviceName: String, interval: Long) = {
      val msInterval = interval * 1000
      (coordActor ? Operation(Restart(Some(msInterval)), hippoMap(serviceName).id)) onSuccess {
        case StateCmdSuccess =>
          println(s"Restart success, interval $interval sec.")
        case StateCmdFailure =>
          println("error, please check state.")
        case HippoNotFound =>
          println("hippo not found.")
        case StateCmdFailure =>
          println("command not handle.")
      }
    }

    def defaultRestart(serviceName: String) = {
      (coordActor ? Operation(Restart(), hippoMap(serviceName).id)) onSuccess {
        case StateCmdSuccess =>
          println("Restart success, interval default 30 sec.")
        case StateCmdFailure =>
          println("error, please check state.")
        case HippoNotFound =>
          println("hippo not found.")
        case StateCmdFailure =>
          println("command not handle.")
      }
    }
  }
}
