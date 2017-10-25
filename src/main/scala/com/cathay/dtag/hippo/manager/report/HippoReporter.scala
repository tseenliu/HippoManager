package com.cathay.dtag.hippo.manager.report

import akka.actor.{Actor, ActorLogging, Terminated}
import cakesolutions.kafka.akka.KafkaConsumerActor.{Confirm, Subscribe}
import cakesolutions.kafka.akka.{ConsumerRecords, KafkaConsumerActor}
import com.typesafe.config.Config
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import spray.json._
import JsonProtocol._
import akka.actor.ActorRef

/**
  * Created by Tse-En on 2017/9/7.
  */
class HippoReporter(config: Config, receiver: ActorRef) extends Actor with ActorLogging {

  private val consumerConf = config.getConfig("kafka.consumer")
  private val topics: Array[String] = config.getStringList("hippo.subscribe-topics").toArray().map(_.toString)

  // Records' type of [key, value]
  val recordsExt = ConsumerRecords.extractor[String, String]

  val consumer = context.actorOf(
    KafkaConsumerActor.props(
      consumerConf,
      new StringDeserializer,
      new StringDeserializer,
      self
    ), "Reporter"
  )
  consumer ! Subscribe.AutoPartition(topics)
  context.watch(consumer)

  override def receive: Receive = {
    case Terminated(watchActor) => println(s"[ERROR] ${watchActor.path} to be killed.")

    // Records from Kafka
    case recordsExt(records) =>
      sender ! Confirm(records.offsets, commit = true)
      processRecords(records.recordsList)
  }

  //override def to customize process value of consumer
  protected def processRecords(recordsList: List[ConsumerRecord[String, String]]): Unit = {
    recordsList.foreach { r =>
      try {
        // Parse records in Json format
        val message: ReportMessage = r.value().parseJson.convertTo[ReportMessage]
        processValue(message)
      } catch {
        case e: Exception =>
          println(e)
          log.error(s"${r.value()} not a JASON format")
      }
    }
  }

  protected def processValue(message: ReportMessage): Unit = {
    if (message.is_success == 1) {
      //context.actorSelection("../entry-state") ! message
      receiver ! message
    }  else {
      log.error(s"exit code: ${message.is_success}, monitor is not healthy.")
    }
  }
}
