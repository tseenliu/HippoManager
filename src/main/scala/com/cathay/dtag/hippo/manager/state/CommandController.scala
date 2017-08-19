package com.cathay.dtag.hippo.manager.state

import scala.util.Random


// SSH result
case class BashResult(code: Int, pid: Option[Int], echo: String="") {
  def isSuccess: Boolean = code == 0
}

class CommandController(conf: HippoConfig) {
  private val rand = new Random()

  // For testing
  private def callFakeRandomCommand: BashResult = {
    val code = rand.nextInt(2)
    val pid = new Random().nextInt(65536)
    val echo = if (code == 0) "stdout" else "stderr"
    BashResult(code, Some(pid), echo)
  }

  // TODO: sh start.sh
  def startHippo: BashResult = {
    callFakeRandomCommand
  }

  // TODO: sh restart.sh
  def restartHippo: BashResult = {
    callFakeRandomCommand
  }

  // TODO: sh stop.sh
  def stopHippo: BashResult = {
    callFakeRandomCommand
  }

  // TODO: ps aux
  def checkHippo: BashResult = {
    callFakeRandomCommand
  }
}
