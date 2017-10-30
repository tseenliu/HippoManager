package com.cathay.dtag.hippo.manager.state

import com.cathay.dtag.hippo.manager.core.schema.HippoConfig

import scala.util.Random
import sys.process._


// SSH result
case class BashResult(code: Int, pid: Option[Int], echo: String="") {
  def isSuccess: Boolean = code == 0
}

case class BashHandler(exitCode: Int, stdout: StringBuilder, stderr: StringBuilder)

class CommandController(coordAddress: String, conf: HippoConfig) {
  private val servicePath = s"${conf.path}"
  private val startPattern = s"Monitor-${conf.name} pid : ([0-9]+)\n${conf.name} pid : ([0-9]+)".r
  private val stopPattern =   s"Stopping Monitor-${conf.name} successfully , whose pid is ([0-9]+)\nStopping ${conf.name} successfully , whose pid is ([0-9]+)".r
  private val restartPattern = s"${stopPattern.toString()}\n${startPattern.toString()}".r
  private val statusPattern = s"Monitor-${conf.name} is running : ([0-9]+)\n${conf.name} is running : ([0-9]+)".r

  def runHippoPlugin(option: String, checkInterval: Long = 0): BashHandler = {
    val stdout = new StringBuilder
    val stderr = new StringBuilder
    option match {
      case "start" =>
        val exitCode = Seq("/bin/sh", s"$servicePath/hippo/bin/monitor-start", "-u", conf.user, "-c", coordAddress, "-i", checkInterval.toString, conf.name) !
          ProcessLogger(stdout append _ + "\n", stderr append _ + "\n")
        BashHandler(exitCode, stdout, stderr)
      case "restart" =>
        val exitCode = Seq("/bin/sh", s"$servicePath/hippo/bin/monitor-start", "-u", conf.user, "-c", coordAddress, "-r", "-i", checkInterval.toString, conf.name) !
          ProcessLogger(stdout append _ + "\n", stderr append _ + "\n")
        BashHandler(exitCode, stdout, stderr)
      case "stop" =>
        val exitCode = Seq("/bin/sh", s"$servicePath/hippo/bin/monitor-stop", conf.name) !
          ProcessLogger(stdout append _ + "\n", stderr append _ + "\n")
        BashHandler(exitCode, stdout, stderr)
      case "check" =>
        val exitCode = Seq("/bin/sh", s"$servicePath/hippo/bin/monitor-status", conf.name) !
          ProcessLogger(stdout append _ + "\n", stderr append _ + "\n")
        BashHandler(exitCode, stdout, stderr)
    }
  }

  // TODO: sh start.sh
  def startHippo(checkInterval: Long): BashResult = {
    val handler = runHippoPlugin("start", checkInterval)

    if (handler.exitCode == 0 && handler.stdout.nonEmpty) {
      val startPattern(monitor, hippo) = handler.stdout.toString.trim
      BashResult(handler.exitCode, Some(monitor.toInt), handler.stdout.toString())
    } else {
      println(handler.stderr.toString.trim)
      BashResult(handler.exitCode, None, handler.stderr.toString)
    }
  }

  // TODO: sh restart.sh
  def restartHippo(checkInterval: Long): BashResult = {
    println(s"interval in restart:${checkInterval.toString}")
    val handler = runHippoPlugin("restart", checkInterval)

    if (handler.exitCode == 0 && handler.stdout.nonEmpty) {
      val restartPattern(oldMonitor, oldHippo, monitor, hippo) = handler.stdout.toString.trim
      BashResult(handler.exitCode, Some(monitor.toInt), handler.stdout.toString())
    } else {
      println(handler.stderr.toString.trim)
      BashResult(handler.exitCode, None, handler.stderr.toString)
    }
  }

  // TODO: sh stop.sh
  def stopHippo: BashResult = {
    val handler = runHippoPlugin("stop")

    if (handler.exitCode == 0 && handler.stdout.nonEmpty) {
      val stopPattern(monitor, hippo) = handler.stdout.toString.trim
      BashResult(handler.exitCode, Some(monitor.toInt), handler.stdout.toString())
    } else {
      println(handler.stderr.toString.trim)
      BashResult(handler.exitCode, None, handler.stderr.toString)
    }
  }

  // TODO: ps aux
  def checkHippo: BashResult = {
    val handler = runHippoPlugin("check")

    if (handler.exitCode == 0 && handler.stdout.nonEmpty) {
      val statusPattern(monitor, hippo) = handler.stdout.toString.trim
      BashResult(handler.exitCode, Some(monitor.toInt), handler.stdout.toString())
    } else {
      println(handler.stderr.toString.trim)
      BashResult(handler.exitCode, None, handler.stderr.toString)
    }
  }

}

