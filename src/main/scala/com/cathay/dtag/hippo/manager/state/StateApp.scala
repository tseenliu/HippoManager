package com.cathay.dtag.hippo.manager.state

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.util.Random


object StateApp extends App {
  import HippoConfig.Command._

  val r = new Random()
  val system = ActorSystem("StateTest")
  implicit val timeout = Timeout(5 seconds)

  def createHippoActorRef(conf: HippoConfig): ActorRef = {
    println(s"${conf.id} is created at ${conf.location}")
    system.actorOf(Props(new HippoFSM(conf)), name = conf.id)
  }

  def sleepRandom(maxSeconds: Int=2000): Unit =
    Thread.sleep(r.nextInt(maxSeconds))

  val hConf = HippoConfig("edge1", "batchetl.journey", "/app/journey")
  val hippoFSM = createHippoActorRef(hConf)

  hippoFSM ! PrintStatus
  sleepRandom()

  hippoFSM ! Start()
  hippoFSM ! PrintStatus
  sleepRandom()

  hippoFSM ! Report
  hippoFSM ! PrintStatus
  sleepRandom()

  hippoFSM ! Stop
  hippoFSM ! PrintStatus
  sleepRandom()

  hippoFSM ! Start()
  hippoFSM ! PrintStatus
  sleepRandom()

  hippoFSM ! Restart
  hippoFSM ! PrintStatus
  sleepRandom()

  //hippoFSM ! Remove
}
