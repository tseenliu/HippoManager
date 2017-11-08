package com.cathay.dtag.hippo.manager.coord

import java.io.{FileNotFoundException, IOException}
import java.nio.file.{Files, Paths}

import scala.io.Source
import sys.process._
/**
  * Created by Tse-En on 2017/11/7.
  */
class KeyGenerator {
  val sshDir = s"${System.getProperty("user.home")}/.ssh"
  val exist = Files.exists(Paths.get(s"$sshDir/id_rsa.pub"))

  def getKey: String = {
    var rsa: String = null
    val stdout = new StringBuilder
    val stderr = new StringBuilder

    if (!exist) {
      val exitCode = Seq("ssh-keygen", "-f", s"$sshDir/id_rsa", "-t", "rsa", "-N", "") !
        ProcessLogger(stdout append _ + "\n", stderr append _ + "\n")

      exitCode match {
        case 0 => println(s"$stdout")
          rsa = readRsaKeyFile()
        case 1 => println(s"$stderr")
      }
    } else {
      rsa = readRsaKeyFile()
    }
    rsa
  }

  def readRsaKeyFile(): String = {
    var tmp: String = null
    try {
      for (line <- Source.fromFile(s"$sshDir/id_rsa.pub").getLines) {
        tmp = line
      }
    } catch {
      case e: FileNotFoundException => println(s"Couldn't find [$sshDir/id_rsa.pub] file.")
      case e: IOException => println("Got an IOException!")
    }
    tmp
  }

}
