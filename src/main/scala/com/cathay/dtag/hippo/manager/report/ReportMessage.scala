package com.cathay.dtag.hippo.manager.report

import spray.json.DefaultJsonProtocol

/**
  * Created by Tse-En on 2017/9/8.
  */
case class ReportMessage (
                           host: String,
                           path: String,
                           service_name: String,
                           monitor_pid: String,
                           service_pid: String,
                           exec_time: String,
                           is_success: String,
                           error_msg: String
                         )

object JsonProtocol extends DefaultJsonProtocol {
  implicit val frontierFormat = jsonFormat8(ReportMessage)
}