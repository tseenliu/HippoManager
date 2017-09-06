package com.cathay.dtag.hippo.manager.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.cathay.dtag.hippo.manager.conf.HippoConfig.Response
import com.cathay.dtag.hippo.manager.conf.HippoConfig.Response._
import com.cathay.dtag.hippo.manager.conf.{HippoConfig, HippoGroup, HippoInstance}
import spray.json._


case class CommandParams(host: String,
                         serviceName: String,
                         path: Option[String],
                         interval: Option[Long])

trait HippoJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val commandFormat = jsonFormat4(CommandParams)

  // HippoConfig
  implicit object ConfigFormat extends RootJsonFormat[HippoConfig] {
    override def write(obj: HippoConfig): JsValue = {
      JsObject(
        "host" -> JsString(obj.host),
        "serviceName" -> JsString(obj.name),
        "path" -> JsString(obj.path),
        "execTime" -> JsNumber(obj.execTime)
      )
    }

    override def read(json: JsValue): HippoConfig = {
      json.asJsObject.getFields("host", "serviceName", "path") match {
        case Seq(JsString(host), JsString(name), JsString(path)) =>
          HippoConfig(host, name, path)
        case _ =>
          deserializationError("Hippo Config parse error.")
      }
    }
  }

  implicit object InstanceFormat extends RootJsonFormat[HippoInstance] {
    override def write(obj: HippoInstance): JsValue = {
      JsObject(
        "id" -> JsString(obj.conf.id),
        "config" -> obj.conf.toJson,
        "interval" -> JsNumber(obj.checkInterval),
        "lastUpdateTime" -> JsNumber(obj.lastUpdateTime),
        "pid" -> (if (obj.monitorPID.isDefined) JsNumber(obj.monitorPID.get) else JsNull),
        "state" -> JsString(obj.state)
      )
    }

    override def read(value: JsValue): HippoInstance = {
      val jso = value.asJsObject
      val conf = jso.fields("config").convertTo[HippoConfig]
      jso.getFields("interval", "lastUpdateTime", "pid", "state") match {
        case Seq(JsNumber(interval), JsNumber(updatedAt), JsNull, JsString(state)) =>
          HippoInstance(conf, interval.asInstanceOf[Long], updatedAt.asInstanceOf[Long],
            None, state)
        case Seq(JsNumber(interval), JsNumber(updatedAt), JsNumber(pid), JsString(state)) =>
          HippoInstance(conf, interval.asInstanceOf[Long], updatedAt.asInstanceOf[Long],
            Some(pid.asInstanceOf[Int]), state)
        case _ =>
          deserializationError("Hippo Instance parse error.")
      }
    }
  }

  implicit object GroupFormat extends RootJsonFormat[HippoGroup] {
    override def write(obj: HippoGroup): JsValue = {
      JsObject(
        "nodeAddress" -> JsString(obj.nodeAddress),
        "snapshotTime" -> JsNumber(obj.createdAt),
        "instances" -> JsArray(obj.group.values.map(_.toJson).toSeq: _*)
      )
    }

    override def read(json: JsValue): HippoGroup = {
      val address = json.asJsObject.fields("nodeAddress")
        .convertTo[String]
      val group = json.asJsObject.fields("instances")
        .convertTo[Seq[HippoInstance]]
        .map(inst => inst.conf.id -> inst).toMap
      HippoGroup(address, group)
    }
  }
}