package com.overviewdocs.jobhandler.documentset

import akka.actor.Props
import akka.pattern.ask
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.Future

import com.overviewdocs.database.{DocumentSetDeleter,DocumentSetCreationJobDeleter}
import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.test.ActorSystemContext

class DocumentSetCommandWorkerSpec extends Specification with Mockito {
  sequential

  "DocumentSetCommandWorker" should {
    trait BaseScope extends ActorSystemContext {
      val broker = testActor
      val documentSetDeleter = smartMock[DocumentSetDeleter]
      val documentSetCreationJobDeleter = smartMock[DocumentSetCreationJobDeleter]
      val subject = system.actorOf(Props(classOf[DocumentSetCommandWorker], broker, documentSetDeleter, documentSetCreationJobDeleter))
      val duration = scala.concurrent.duration.Duration(1, "s")
    }

    "send WorkerReady on start" in new BaseScope {
      expectMsg(DocumentSetMessageBroker.WorkerReady)
    }

    "call documentSetDeleter.delete" in new BaseScope {
      documentSetDeleter.delete(1L) returns Future.successful(())
      subject ! DocumentSetCommands.DeleteDocumentSet(1L)
      receiveN(2, duration) // Initial WorkerReady, then second WorkerReady
      there was one(documentSetDeleter).delete(1L)
    }

    "call documentSetCreationJobDeleter.delete" in new BaseScope {
      documentSetCreationJobDeleter.delete(2L) returns Future.successful(())
      subject ! DocumentSetCommands.DeleteDocumentSetJob(1L, 2L)
      receiveN(2, duration) // Initial WorkerReady, then second WorkerReady
      there was one(documentSetCreationJobDeleter).delete(2L)
    }
  }
}
