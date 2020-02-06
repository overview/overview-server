package com.overviewdocs.ingest.ingest

import akka.actor.ActorRef
import akka.stream.scaladsl.{Sink,Source}
import java.time.Instant
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext,Future,Promise,blocking}

import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.ingest.model.{ProcessedFile2,IngestedRootFile2,ResumedFileGroupJob,FileGroupProgressState}
import com.overviewdocs.test.ActorSystemContext
import com.overviewdocs.test.factories.{PodoFactory=>factory}

class IngesterSpec extends Specification with Mockito {
  sequential

  protected def await[T](future: Future[T]): T = {
    blocking(scala.concurrent.Await.result(future, scala.concurrent.duration.Duration("2s")))
  }

  trait BaseScope extends Scope with ActorSystemContext {
    implicit val ec = system.dispatcher

    val mockFile2Writer = mock[File2Writer]

    val batchSize: Int = 1
    val maxBatchWait: FiniteDuration = FiniteDuration(1, "ms")
    lazy val ingester = Ingester.ingest(mockFile2Writer, batchSize, maxBatchWait)

    val fileGroup = factory.fileGroup(addToDocumentSetId=Some(2L), nFiles=Some(999))
    val fileGroupJob = ResumedFileGroupJob(
      fileGroup,
      new FileGroupProgressState(fileGroup, 0, 0L, Instant.now, _ => (), Promise[akka.Done]()),
      mock[ActorRef],
      "message"
    )
    val input: Vector[ProcessedFile2]

    lazy val output = {
      Source(input)
        .via(ingester)
        .runWith(Sink.seq[IngestedRootFile2])
    }

    lazy val result = await(output)
  }

  "Ingester" should {
    "process a root" in new BaseScope {
      val input1 = ProcessedFile2(1L, fileGroupJob, None, 0, 0)
      override val input = Vector(input1)

      val output1 = IngestedRootFile2(1L, fileGroupJob)
      mockFile2Writer.ingestBatch(input)(ec) returns Future.unit
      result must beEqualTo(Vector(output1))
    }

    "emit a child and root" in new BaseScope {
      val input1 = ProcessedFile2(1L, fileGroupJob, None, 1, 0)
      val input2 = ProcessedFile2(2L, fileGroupJob, Some(1L), 0, 0)
      override val input = Vector(input1, input2)

      val output1 = IngestedRootFile2(1L, fileGroupJob)
      mockFile2Writer.ingestBatch(Vector(input2, input1.copy(nIngestedChildren=1)))(ec) returns Future.unit
      result must beEqualTo(Vector(output1))
    }

    "not ingest root before a child is ingested" in new BaseScope {
      val input1 = ProcessedFile2(1L, fileGroupJob, None, 2, 0)
      val input2 = ProcessedFile2(2L, fileGroupJob, Some(1L), 0, 0)
      override val input = Vector(input1, input2)

      mockFile2Writer.ingestBatch(Vector(input2))(ec) returns Future.unit
      result must beEqualTo(Vector())
    }

    "ingest root after holding it for a while" in new BaseScope {
      val input1 = ProcessedFile2(1L, fileGroupJob, None, 2, 0)
      val input2 = ProcessedFile2(2L, fileGroupJob, Some(1L), 0, 0)
      val input3 = ProcessedFile2(3L, fileGroupJob, None, 0, 0) // a separate root
      val input4 = ProcessedFile2(4L, fileGroupJob, Some(1L), 0, 0)
      override val input = Vector(input1, input2, input3, input4)

      val output1 = IngestedRootFile2(1L, fileGroupJob)
      val output3 = IngestedRootFile2(3L, fileGroupJob)
      mockFile2Writer.ingestBatch(Vector(input2))(ec) returns Future.unit
      mockFile2Writer.ingestBatch(Vector(input3))(ec) returns Future.unit
      mockFile2Writer.ingestBatch(Vector(input4, input1.copy(nIngestedChildren=2)))(ec) returns Future.unit
      result must beEqualTo(Vector(output3, output1))
    }

    "ingest child before root appears" in new BaseScope {
      val input1 = ProcessedFile2(1L, fileGroupJob, Some(2L), 0, 0) // child 1: processed before parent
      val input2 = ProcessedFile2(2L, fileGroupJob, None, 2, 0)     // parent: processed
      val input3 = ProcessedFile2(3L, fileGroupJob, Some(2L), 0, 0) // child 2: processed after parent
      override val input = Vector(input1, input2, input3)

      mockFile2Writer.ingestBatch(Vector(input1))(ec) returns Future.unit
      mockFile2Writer.ingestBatch(Vector(input3, input2.copy(nIngestedChildren=2)))(ec) returns Future.unit
      result must beEqualTo(Vector(IngestedRootFile2(2L, fileGroupJob)))
    }

    "process in batches" in new BaseScope {
      override val batchSize = 3

      val input1 = ProcessedFile2(1L, fileGroupJob, None, 2, 0)
      val input2 = ProcessedFile2(2L, fileGroupJob, Some(1L), 0, 0)
      val input3 = ProcessedFile2(3L, fileGroupJob, None, 0, 0) // a separate root
      val input4 = ProcessedFile2(4L, fileGroupJob, Some(1L), 0, 0)
      override val input = Vector(input1, input2, input3, input4)

      val output1 = IngestedRootFile2(1L, fileGroupJob)
      val output3 = IngestedRootFile2(3L, fileGroupJob)
      mockFile2Writer.ingestBatch(Vector(input2, input3))(ec) returns Future.unit
      mockFile2Writer.ingestBatch(Vector(input4, input1.copy(nIngestedChildren=2)))(ec) returns Future.unit
      result must beEqualTo(Vector(output3, output1))
    }
  }
}
