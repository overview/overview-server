package com.overviewdocs.jobhandler.documentcloud

import akka.actor.{ActorRef,ActorSystem}
import akka.testkit.{TestActorRef,TestProbe}
import org.specs2.mutable.Specification
import scala.concurrent.duration.Duration

import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.models.DocumentCloudImport
import com.overviewdocs.test.ActorSystemContext
import com.overviewdocs.test.factories.{PodoFactory=>factory}

class DocumentCloudImportWorkBrokerSpec extends Specification {
  sequential

  import DocumentCloudImportWorkBroker._

  "DocumentCloudImportWorkBroker" should {
    trait BaseScope extends ActorSystemContext {
      val subject = TestActorRef(DocumentCloudImportWorkBroker.props)
      val broker: DocumentCloudImportWorkBroker = subject.underlyingActor
      val worker1 = TestProbe()
      val worker2 = TestProbe()

      def addCommand(documentCloudImport: DocumentCloudImport) = DocumentSetCommands.AddDocumentsFromDocumentCloud(documentCloudImport)
    }

    "give a job to a sender if there is one pending" in new BaseScope {
      val ci = factory.documentCloudImport()
      subject ! addCommand(ci)
      subject.tell(WorkerReady, worker1.ref)
      worker1.expectMsg(addCommand(ci))
    }

    "give a job to a sender when it becomes available" in new BaseScope {
      val ci = factory.documentCloudImport()
      subject.tell(WorkerReady, worker1.ref)
      subject ! addCommand(ci)
      worker1.expectMsg(addCommand(ci))
    }

    "queue workers" in new BaseScope {
      val ci1 = factory.documentCloudImport()
      val ci2 = factory.documentCloudImport()
      subject.tell(WorkerReady, worker1.ref)
      subject.tell(WorkerReady, worker2.ref)
      subject ! addCommand(ci1)
      worker1.expectMsg(addCommand(ci1))
      subject ! addCommand(ci2)
      worker2.expectMsg(addCommand(ci2))
    }

    "queue commands" in new BaseScope {
      val ci1 = factory.documentCloudImport()
      val ci2 = factory.documentCloudImport()
      subject ! addCommand(ci1)
      subject ! addCommand(ci2)
      subject.tell(WorkerReady, worker1.ref)
      subject.tell(WorkerReady, worker2.ref)
      worker1.expectMsg(addCommand(ci1))
      worker2.expectMsg(addCommand(ci2))
    }

    "send an ack message" in new BaseScope {
      val receiver = TestProbe()
      val ci = factory.documentCloudImport()
      subject ! DoWorkThenAck(addCommand(ci), receiver.ref, "ack")
      subject.tell(WorkerReady, worker1.ref)
      worker1.expectMsg(addCommand(ci))
      receiver.expectNoMessage(Duration.Zero)
      subject.tell(WorkerDone(addCommand(ci)), worker1.ref)
      receiver.expectMsg("ack")
    }
  }
}
