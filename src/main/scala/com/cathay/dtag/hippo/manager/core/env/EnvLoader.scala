package com.cathay.dtag.hippo.manager.core.env

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}

trait EnvLoader {

  var configDir: String = "config"

  def getConfig(name: String): Config =
    ConfigFactory.parseFile(new File(s"${this.configDir}/$name.conf"))
}
