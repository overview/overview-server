package org.overviewproject.reclustering

import org.reactivestreams.Subscriber
import scala.concurrent.{ExecutionContext,Future}
import slick.backend.DatabasePublisher

import org.overviewproject.models.Document
import org.overviewproject.util.DocumentConsumer
import org.overviewproject.util.Progress.ProgressAbortFn
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.overviewproject.util.Progress.Progress
import org.overviewproject.util.DocumentSetCreationJobStateDescription.Retrieving

class ReclusteringDocumentProducerSpec extends Specification with Mockito {

  "ReclusteringDocumentProducer" should {
    class TestReclusteringDocumentProducer(
      override protected val consumer: DocumentConsumer,
      override protected val progAbort: ProgressAbortFn,
      override protected val nDocuments: Int,
      override protected val documentStream: DatabasePublisher[(Long,String)]
    ) extends ReclusteringDocumentProducer {
      override protected val progressReportThrottle: Long = -1 // always check progress
      override protected val ec: ExecutionContext = ExecutionContext.Implicits.global
    }

    class DocumentPublisher(documents: Seq[(Long,String)]) extends DatabasePublisher[(Long,String)] {
      override def subscribe(arg0: Subscriber[_ >: (Long,String)]): Unit = ??? // We're not *really* streaming

      override def foreach[U](f: (Tuple2[Long,String]) => U)(implicit ec: ExecutionContext): Future[Unit] = {
        // Our _real_ mocking goes here
        Future(documents.foreach(f))(ec) // Future() wraps a control-flow exception, AbortedException
      }
    }

    trait BaseScope extends Scope {
      val documentSetId = 123L
      val maybeTagId: Option[Long] = None
      val consumer = smartMock[DocumentConsumer]
      val progAbort = mock[ProgressAbortFn] // smartMock triggers bug https://code.google.com/p/mockito/issues/detail?id=107
      val documents = Seq(
        (1L, "doc1"),
        (2L, "doc2"),
        (3L, "doc3")
      )
      lazy val documentStream = new DocumentPublisher(documents)
      lazy val subject = new TestReclusteringDocumentProducer(consumer, progAbort, documents.length, documentStream)
    }

    "consume documents" in new BaseScope {
      progAbort.apply(any) returns false

      val numberOfDocumentsProduced = subject.produce
      numberOfDocumentsProduced must beEqualTo(3)
      there was one(consumer).processDocument(1, "doc1")
      there was one(consumer).processDocument(2, "doc2")
      there was one(consumer).processDocument(3, "doc3")
    }

    "report progress" in new BaseScope {
      progAbort.apply(any) returns false

      subject.produce
      there was one(progAbort).apply(Progress(0.5 * 1 / 3, Retrieving(1, 3)))
      there was one(progAbort).apply(Progress(0.5 * 2 / 3, Retrieving(2, 3)))
      there was one(progAbort).apply(Progress(0.5 * 3 / 3, Retrieving(3, 3)))
    }

    "call productionComplete" in new BaseScope {
      progAbort.apply(any) returns false
      subject.produce

      there was one(consumer).productionComplete
    }

    "stop processing when cancelled" in new BaseScope {
      progAbort.apply(any) returns false thenReturn true

      subject.produce

      there was one(consumer).processDocument(1, "doc1")
      there was one(consumer).processDocument(2, "doc2")
      there was no(consumer).processDocument(3, "doc3")
    }
  }
}
