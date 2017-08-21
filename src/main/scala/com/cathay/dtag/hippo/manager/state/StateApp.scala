package com.cathay.dtag.hippo.manager.state

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import akka.pattern.ask
import com.cathay.dtag.hippo.manager.conf.HippoConfig

import scala.concurrent.duration._
import scala.util.{Failure, Random, Success}


object StateApp extends App {
  import HippoConfig.EntryCommand._
  import HippoConfig.Response._

  val r = new Random()
  val system = ActorSystem("StateTest")
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val timeout = Timeout(5 seconds)

  def createHippoActorRef(conf: HippoConfig): ActorRef = {
    println(s"${conf.id} is created at ${conf.location}")
    system.actorOf(Props(new HippoStateActor(conf)), name = conf.id)
  }

  def sleepRandom(maxSeconds: Int=2000): Unit =
    Thread.sleep(r.nextInt(maxSeconds) + 1000)

  val entry = system.actorOf(Props(new EntryStateActor("localhost")), name="entry")

  sleepRandom()

  val hConf = HippoConfig("edge1", "batchetl.journey", "/app/journey", checkInterval = 30*1000)

  (entry ? Register(hConf)) onSuccess {
    case EntryCmdSuccess =>
      println("register successfully")
    case HippoExists =>
      println("HippoExists")
  }
  sleepRandom()

//  (entry ? Register(hConf)) onSuccess {
//    case EntryCmdSuccess =>
//      println("register successfully")
//    case HippoExists =>
//      println("HippoExists")
//  }
//  sleepRandom()

//  (entry ? GetNodeStatus).mapTo[Map[String, HippoInstance]].onComplete {
//    case Success(hippos) =>
//      hippos.foreach(println)
//    case Failure(e) =>
//      println(e.getMessage)
//  }
//
//  entry ! Operation(Start(), hConf.id)
//  (entry ? Operation(GetStatus, hConf.id)) onSuccess {
//    case hi: HippoInstance =>
//      println(hi)
//    case HippoNotFound =>
//      println("register successfully")
//  }


//  val hConf = HippoConfig("edge1", "batchetl.journey", "/app/journey", checkInterval = 3)
//  val hippoFSM = createHippoActorRef(hConf)
//
//  hippoFSM ! PrintStatus
//  sleepRandom()
//
//  hippoFSM ! Start()
//  hippoFSM ! PrintStatus
//  sleepRandom()
//
//  hippoFSM ! Report
//  hippoFSM ! PrintStatus
//  sleepRandom()
//
//  hippoFSM ! Restart
//  hippoFSM ! PrintStatus
//  sleepRandom()
//
//  hippoFSM ! Report
//  hippoFSM ! PrintStatus
//  sleepRandom()
//
//  hippoFSM ! Report
//  hippoFSM ! PrintStatus
//  sleepRandom()
//
//  hippoFSM ! Stop
//  hippoFSM ! PrintStatus
//  sleepRandom()
//
//  hippoFSM ! Start()
//  hippoFSM ! PrintStatus
//  sleepRandom()
//
//  hippoFSM ! Delete
}
