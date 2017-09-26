package com.cathay.dtag.hippo.manager.app.test

import java.io.File

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

/**
  * Created by Tse-En on 2017/9/7.
  */
object TestReport extends App {

  //val configPath = args(0)
  //val reporterConfig = ConfigFactory.parseFile(new File(configPath))
  val config = ConfigFactory.parseFile(new File("config/reporter.conf"))

  val akkaSystem = ActorSystem("reporter-try")
  //val clientActor = akkaSystem.actorOf(Props(new HippoReporter(config)),"KafakClient")

}
