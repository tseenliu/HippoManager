package com.cathay.dtag.hippo.manager.state

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import com.cathay.dtag.hippo.manager.state.HippoFSM.KillSuccess
import com.typesafe.config.ConfigFactory

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

  val hConf = HippoConfig("edge1", "batchetl.journey", "/app/journey")
  val hippoFSM = createHippoActorRef(hConf)

  hippoFSM ! PrintState

  Thread.sleep(r.nextInt(2000))

  hippoFSM ! Start()
  hippoFSM ! PrintState

  Thread.sleep(r.nextInt(2000))

  hippoFSM ! Report
  hippoFSM ! PrintState

  Thread.sleep(r.nextInt(2000))

  hippoFSM ! Report
  hippoFSM ! PrintState

  Thread.sleep(r.nextInt(2000))

  hippoFSM ! Stop
  hippoFSM ! PrintState

  Thread.sleep(r.nextInt(2000))

  hippoFSM ! Start()
  hippoFSM ! PrintState

  Thread.sleep(r.nextInt(2000))

  hippoFSM ! Restart
  hippoFSM ! PrintState

  Thread.sleep(r.nextInt(2000))

  //hippoFSM ! Remove
}
