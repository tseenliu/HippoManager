package com.cathay.dtag.hippo.manager.app.test

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.cathay.dtag.hippo.manager.core.schema.{HippoConfig, HippoGroup}
import com.cathay.dtag.hippo.manager.state.{EntryStateActor, HippoStateActor}

import scala.concurrent.duration._
import scala.util.{Failure, Random, Success}


object TestState extends App {
  import HippoConfig.EntryCommand._

  val r = new Random()
  val system = ActorSystem("StateTest")
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val timeout = Timeout(5 seconds)

  def createHippoActorRef(conf: HippoConfig): ActorRef = {
    println(s"${conf.id} is created at ${conf.location}")
    system.actorOf(Props(new HippoStateActor(
      conf,
      "127.0.0.1",
      HippoConfig.DEFAULT_INTERVAL,
      HippoConfig.CHECK_BUFFER_TIME,
      HippoConfig.CHECK_BUFFER_QUEUE_SIZE
    )), name = conf.id)
  }

  def sleepRandom(maxSeconds: Int=2000): Unit =
    Thread.sleep(r.nextInt(maxSeconds) + 1000)

//  val entry = system.actorOf(Props(new EntryStateActor("localhost")), name="entry")

  sleepRandom()

  val hConf1 = HippoConfig("edge1", "hippos.service.test1", "/Users/Tse-En/Desktop/HippoPlugin/HippoPlugin/test")

//  (entry ? Register(hConf1)) onSuccess {
//    case EntryCmdSuccess =>
//      println("register successfully")
//    case HippoExists =>
//      println("HippoExists")
//  }
//  sleepRandom()
//
//  (entry ? Register(hConf2)) onSuccess {
//    case EntryCmdSuccess =>
//      println("register successfully")
//    case HippoExists =>
//      println("HippoExists")
//  }
//  sleepRandom()
//
//  (entry ? GetNodeStatus).mapTo[HippoGroup].onComplete {
//    case Success(hippos) =>
//      hippos.group.values.foreach(println)
//    case Failure(e) =>
//      println(e.getMessage)
//  }
//  Thread.sleep(5000)
//
//  (entry ? Operation(Start(Some(10000)), hConf1.id)) onSuccess {
//    case StateCmdSuccess =>
//      println("success.")
//    case StateCmdFailure =>
//      println("error, please check state.")
//    case HippoNotFound =>
//      println("hippo not found.")
//  }
//  Thread.sleep(20000)
//  (entry ? Operation(Stop, hConf1.id)) onSuccess {
//    case StateCmdSuccess =>
//      println("success.")
//    case StateCmdFailure =>
//      println("error, please check state.")
//    case HippoNotFound =>
//      println("hippo not found.")
//  }
//  Thread.sleep(5000)
//  (entry ? Operation(Start(), hConf1.id)) onSuccess {
//    case StateCmdSuccess =>
//      println("success.")
//    case StateCmdFailure =>
//      println("error, please check state.")
//    case HippoNotFound =>
//      println("hippo not found.")
//  }
//  Thread.sleep(40000)
//  (entry ? Operation(Restart(Some(15000)), hConf1.id)) onSuccess {
//    case StateCmdSuccess =>
//      println("success.")
//    case StateCmdFailure =>
//      println("error, please check state.")
//    case HippoNotFound =>
//      println("hippo not found.")
//  }
//  Thread.sleep(20000)
//  (entry ? Operation(Start(), hConf1.id)) onSuccess {
//    case StateCmdSuccess =>
//      println("success.")
//    case StateCmdFailure =>
//      println("error, please check state.")
//    case HippoNotFound =>
//      println("hippo not found.")
//  }

//  (entry ? Operation(GetStatus, hConf1.id)) onSuccess {
//    case hi: HippoInstance =>
//      println(hi)
//    case HippoNotFound =>
//      println("hippo not found.")
//  }
//  Thread.sleep(4000)
//  entry ! Operation(Delete, hConf1.id)
//
//  (entry ? Operation(GetStatus, hConf1.id)) onSuccess {
//    case hi: HippoInstance =>
//      println(hi)
//    case HippoNotFound =>
//      println("hippo not found.")
//  }
//  Thread.sleep(5000)
//
//  (entry ? GetNodeStatus).mapTo[HippoGroup].onComplete {
//    case Success(hippos) =>
//      hippos.group.values.foreach(println)
//    case Failure(e) =>
//      println(e.getMessage)
//  }

  //  Thread.sleep(5000)
//  (entry ? Operation(PrintStatus, hConf1.id)) onSuccess {
//    case EntryCmdSuccess =>
//      println("yse")
//    case HippoNotFound =>
//      println("no")
//  }




//  val hConf = HippoConfig("edge1", "batchetl.journey", "/app/journey", checkInterval = 3)
//  val hippoFSM = createHippoActorRef(hConf)

//  hippoFSM ! PrintStatus
//  sleepRandom()

//  hippoFSM ! Start(Some(15000))
//  //hippoFSM ! PrintStatus
//  sleepRandom()

//  hippoFSM ! Report
//  hippoFSM ! PrintStatus
//  sleepRandom()

//  hippoFSM ! Restart()
//  hippoFSM ! PrintStatus
//  sleepRandom()

//  hippoFSM ! Stop
//  hippoFSM ! PrintStatus
//  sleepRandom()

//  hippoFSM ! Delete
}
