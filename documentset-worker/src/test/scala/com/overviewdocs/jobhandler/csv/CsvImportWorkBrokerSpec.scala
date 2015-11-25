package com.overviewdocs.jobhandler.csv

import akka.actor.{ActorRef,ActorSystem}
import akka.testkit.{TestActorRef,TestProbe}
import org.specs2.mutable.Specification
import scala.concurrent.duration.Duration

import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.models.CsvImport
import com.overviewdocs.test.ActorSystemContext
import com.overviewdocs.test.factories.{PodoFactory=>factory}

class CsvImportWorkBrokerSpec extends Specification {
  sequential

  import CsvImportWorkBroker._

  "CsvImportWorkBroker" should {
    trait BaseScope extends ActorSystemContext {
      val subject = TestActorRef(CsvImportWorkBroker.props)
      val broker: CsvImportWorkBroker = subject.underlyingActor
      val worker1 = TestProbe()
      val worker2 = TestProbe()

      def addCommand(csvImport: CsvImport) = DocumentSetCommands.AddDocumentsFromCsvImport(csvImport)
    }

    "give a job to a sender if there is one pending" in new BaseScope {
      val ci = factory.csvImport()
      subject ! addCommand(ci)
      subject.tell(WorkerReady, worker1.ref)
      worker1.expectMsg(addCommand(ci))
    }

    "give a job to a sender when it becomes available" in new BaseScope {
      val ci = factory.csvImport()
      subject.tell(WorkerReady, worker1.ref)
      subject ! addCommand(ci)
      worker1.expectMsg(addCommand(ci))
    }

    "queue workers" in new BaseScope {
      val ci1 = factory.csvImport()
      val ci2 = factory.csvImport()
      subject.tell(WorkerReady, worker1.ref)
      subject.tell(WorkerReady, worker2.ref)
      subject ! addCommand(ci1)
      worker1.expectMsg(addCommand(ci1))
      subject ! addCommand(ci2)
      worker2.expectMsg(addCommand(ci2))
    }

    "queue commands" in new BaseScope {
      val ci1 = factory.csvImport()
      val ci2 = factory.csvImport()
      subject ! addCommand(ci1)
      subject ! addCommand(ci2)
      subject.tell(WorkerReady, worker1.ref)
      subject.tell(WorkerReady, worker2.ref)
      worker1.expectMsg(addCommand(ci1))
      worker2.expectMsg(addCommand(ci2))
    }

    "send an ack message" in new BaseScope {
      val receiver = TestProbe()
      val ci = factory.csvImport()
      subject ! DoWorkThenAck(addCommand(ci), receiver.ref, "ack")
      subject.tell(WorkerReady, worker1.ref)
      worker1.expectMsg(addCommand(ci))
      receiver.expectNoMsg(Duration.Zero)
      subject.tell(WorkerDone(addCommand(ci)), worker1.ref)
      receiver.expectMsg("ack")
    }
  }
}
