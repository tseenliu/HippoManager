package com.cathay.dtag.hippo.manager

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
//
//  coordActor ! Cmd(service, Awake)
//  coordActor ! "print"
//
//  val service2 = HippoService("127.0.0.1", "hippos_batchetl_tag")
//  println(service2)
//  coordActor ! "print"
//  coordActor ! Cmd(service2, Awake)
//  coordActor ! Cmd(service2, Awake)
//  coordActor ! "print"
}

object Manager2 extends App {
  val coordActor = Coordinator.initiate(8301)

  val service = HippoService("127.0.0.1", "hippos_batchetl_banktag")
  println(service)

  coordActor ! Cmd(service, Awake)
  coordActor ! "print_local"

  Thread.sleep(15000)
  coordActor ! "print_global"
}