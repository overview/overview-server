package com.overviewdocs.jobhandler.documentset

import akka.actor.Props
import akka.testkit.{TestActorRef,TestProbe}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.duration.Duration
import scala.concurrent.{Future,Promise}

import com.overviewdocs.background.reindex.ReindexActor
import com.overviewdocs.clone.Cloner
import com.overviewdocs.database.DocumentSetDeleter
import com.overviewdocs.jobhandler.csv.CsvImportWorkBroker
import com.overviewdocs.jobhandler.documentcloud.DocumentCloudImportWorkBroker
import com.overviewdocs.jobhandler.filegroup.AddDocumentsWorkBroker
import com.overviewdocs.searchindex.Indexer
import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.test.ActorSystemContext
import com.overviewdocs.test.factories.{PodoFactory=>factory}

class DocumentSetCommandWorkerSpec extends Specification with Mockito {
  sequential

  import DocumentSetMessageBroker._

  "DocumentSetCommandWorker" should {
    trait BaseScope extends ActorSystemContext {
      val broker = TestProbe()
      val addDocumentsWorkBroker = TestProbe()
      val csvImportWorkBroker = TestProbe()
      val documentCloudImportWorkBroker = TestProbe()
      val indexer = TestProbe()
      val reindexer = TestProbe()
      val sortBroker = TestProbe()
      val documentSetDeleter = smartMock[DocumentSetDeleter]
      val cloner = smartMock[Cloner]
      val subject = TestActorRef(DocumentSetCommandWorker.props(
        broker.ref,
        addDocumentsWorkBroker.ref,
        csvImportWorkBroker.ref,
        documentCloudImportWorkBroker.ref,
        indexer.ref,
        reindexer.ref,
        sortBroker.ref,
        cloner,
        documentSetDeleter
      ))
    }

    "send WorkerReady on start" in new BaseScope {
      broker.expectMsg(WorkerReady)
    }

    "AddDocumentsFromFileGroup" should {
      "send WorkerReady immediately" in new BaseScope {
        broker.expectMsg(WorkerReady)

        subject ! DocumentSetCommands.AddDocumentsFromFileGroup(factory.fileGroup(addToDocumentSetId=Some(1L)))
        broker.expectMsg(WorkerReady)
      }

      "queue ack message for when command completes" in new BaseScope {
        broker.expectMsg(WorkerReady)

        val command = DocumentSetCommands.AddDocumentsFromFileGroup(factory.fileGroup(addToDocumentSetId=Some(2L)))
        subject ! command
        // This is one-half of the spec; the other half is in AddDocumentsWorkBroker
        addDocumentsWorkBroker.expectMsg(
          AddDocumentsWorkBroker.DoWorkThenAck(command, broker.ref, WorkerDoneDocumentSetCommand(2L))
        )
      }
    }

    "AddDocumentsFromCsvImport" should {
      "send WorkerReady immediately" in new BaseScope {
        broker.expectMsg(WorkerReady)

        subject ! DocumentSetCommands.AddDocumentsFromCsvImport(factory.csvImport(documentSetId=1L))
        broker.expectMsg(WorkerReady)
      }

      "queue ack message for when command completes" in new BaseScope {
        broker.expectMsg(WorkerReady)

        val command = DocumentSetCommands.AddDocumentsFromCsvImport(factory.csvImport(id=2L, documentSetId=1L))
        subject ! command
        // This is one-half of the spec; the other half is in CsvImportWorkBroker
        csvImportWorkBroker.expectMsg(
          CsvImportWorkBroker.DoWorkThenAck(command, broker.ref, WorkerDoneDocumentSetCommand(1L))
        )
      }
    }

    "AddDocumentsFromDocumentCloud" should {
      "send WorkerReady immediately" in new BaseScope {
        broker.expectMsg(WorkerReady)

        subject ! DocumentSetCommands.AddDocumentsFromDocumentCloud(factory.documentCloudImport(documentSetId=1L))
        broker.expectMsg(WorkerReady)
      }

      "queue ack message for when command completes" in new BaseScope {
        broker.expectMsg(WorkerReady)

        val command = DocumentSetCommands.AddDocumentsFromDocumentCloud(factory.documentCloudImport(id=2, documentSetId=1L))
        subject ! command
        // This is one-half of the spec; the other half is in DocumentCloudImportWorkBroker
        documentCloudImportWorkBroker.expectMsg(
          DocumentCloudImportWorkBroker.DoWorkThenAck(command, broker.ref, WorkerDoneDocumentSetCommand(1L))
        )
      }
    }

    "CloneDocumentSet" should {
      "call cloner.run" in new BaseScope {
        val cloneJob = factory.cloneJob()
        cloner.run(any) returns Future.unit
        subject ! DocumentSetCommands.CloneDocumentSet(cloneJob)
        there was one(cloner).run(cloneJob)
      }

      "return to the broker when complete" in new BaseScope {
        broker.expectMsg(WorkerReady)

        val promise = Promise[Unit]()
        cloner.run(any) returns promise.future
        subject ! DocumentSetCommands.CloneDocumentSet(factory.cloneJob(destinationDocumentSetId=3L))

        broker.expectNoMsg(Duration.Zero)

        promise.success(())
        broker.expectMsg(WorkerDoneDocumentSetCommand(3L))
        broker.expectMsg(WorkerReady)
      }
    }

    "DeleteDocumentSet" should {
      "call documentSetDeleter.delete" in new BaseScope {
        documentSetDeleter.delete(1L) returns Future.unit
        subject ! DocumentSetCommands.DeleteDocumentSet(1L)
        there was one(documentSetDeleter).delete(1L)
      }

      "return to the broker when complete" in new BaseScope {
        broker.expectMsg(WorkerReady)

        val promise = Promise[Unit]()
        documentSetDeleter.delete(1L) returns promise.future
        subject ! DocumentSetCommands.DeleteDocumentSet(1L)

        broker.expectNoMsg(Duration.Zero)

        promise.success(())
        broker.expectMsg(WorkerDoneDocumentSetCommand(1L))
        broker.expectMsg(WorkerReady)
      }
    }

    "ReindexDocument" should {
      "forward to indexer" in new BaseScope {
        val command = DocumentSetCommands.ReindexDocument(1L, 2L)
        subject ! command
        indexer.expectMsgPF(Duration.Zero) { case Indexer.DoWorkThenAck(command1, _, _) => () }
      }
    }

    "Reindex" should {
      "forward to reindexer" in new BaseScope {
        val command = DocumentSetCommands.Reindex(factory.documentSetReindexJob())
        subject ! command
        reindexer.expectMsg(ReindexActor.ReindexNextDocumentSet)
      }
    }

    "CancelJob" should {
      "forward to addDocumentsWorkBroker" in new BaseScope {
        subject ! DocumentSetCommands.CancelAddDocumentsFromFileGroup(1L, 2L)
        addDocumentsWorkBroker.expectMsg(AddDocumentsWorkBroker.CancelJob(2L))
      }
    }
  }
}
