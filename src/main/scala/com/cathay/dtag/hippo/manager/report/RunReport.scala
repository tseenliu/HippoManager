package com.cathay.dtag.hippo.manager.report

import java.io.File

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

/**
  * Created by Tse-En on 2017/9/7.
  */
object RunReport extends App {

  //val configPath = args(0)
  //val reporterConfig = ConfigFactory.parseFile(new File(configPath))
  val config = ConfigFactory.parseFile(new File("config/reporter.conf"))

  val akkaSystem = ActorSystem("reporter-try")
  val clientActor = akkaSystem.actorOf(Props(new HippoReporter(config)),"KafakClient")

}
