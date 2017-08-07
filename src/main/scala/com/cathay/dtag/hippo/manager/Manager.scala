package com.cathay.dtag.hippo.manager

import java.net.InetAddress

import com.cathay.dtag.hippo.manager.HipposState._


object Manager extends App {
  val coordActor = Coordinator.initiate(8300)

  val service = HippoService("127.0.0.1", "hippos_batchetl_journey")
  println(service)

  coordActor ! "print_local"
  coordActor ! Cmd(service, Awake)
  coordActor ! Cmd(service, Kill)
  coordActor ! "print_local"
  coordActor ! "print_global"

  Thread.sleep(15000)

  coordActor ! "print_global"
}

object Manager2 extends App {
  val coordActor = Coordinator.initiate(8301)

  val service = HippoService(InetAddress.getLocalHost.getHostAddress,
    "hippos_batchetl_banktag")
  println(service)

  coordActor ! Cmd(service, Awake)
  coordActor ! "print_local"

  Thread.sleep(15000)
  coordActor ! "print_global"
}