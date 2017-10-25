package com.cathay.dtag.hippo.manager.report

import spray.json.DefaultJsonProtocol

/**
  * Created by Tse-En on 2017/9/8.
  */
// TODO: schema type should not all be String
case class ReportMessage (clientIP: String,
                          path: String,
                          service_name: String,
                          user: Option[String],
                          monitor_pid: Int,
                          service_pid: Int,
                          exec_time: Long,
                          is_success: Int,
                          error_msg: String,
                          coordAddress: String,
                          interval: Long
                          )

object JsonProtocol extends DefaultJsonProtocol {
  implicit val frontierFormat = jsonFormat11(ReportMessage)
}