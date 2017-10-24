package com.cathay.dtag.hippo.manager.report

import spray.json.DefaultJsonProtocol

/**
  * Created by Tse-En on 2017/9/8.
  */
// TODO: schema type should not all be String
case class ReportMessage (clientIP: String,
                          path: String,
                          service_name: String,
                          monitor_pid: String,
                          service_pid: String,
                          exec_time: String,
                          is_success: String,
                          error_msg: String
                          //coordAdress: String,
                          //interval: Long
                          )

object JsonProtocol extends DefaultJsonProtocol {
  implicit val frontierFormat = jsonFormat8(ReportMessage)
}