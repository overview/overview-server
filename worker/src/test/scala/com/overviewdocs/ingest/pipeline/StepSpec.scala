package com.overviewdocs.ingest.pipeline

import akka.stream.{ActorMaterializer,OverflowStrategy}
import akka.stream.scaladsl.{Keep,Sink,Source,SourceQueueWithComplete}
import akka.util.ByteString
import org.mockito.invocation.InvocationOnMock
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.{JsObject,JsString}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext,Future,Promise,blocking}

import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.ingest.models.{BlobStorageRefWithSha1,CreatedFile2,WrittenFile2,ProcessedFile2}
import com.overviewdocs.models.{BlobStorageRef,File2}
import com.overviewdocs.test.ActorSystemContext

class StepSpec extends Specification with Mockito {
  sequential

  protected def await[T](future: Future[T]): T = {
    blocking(scala.concurrent.Await.result(future, scala.concurrent.duration.Duration("2s")))
  }

  trait BaseScope extends Scope with ActorSystemContext {
    implicit val ec = system.dispatcher

    // Mock File2Writer: all methods (except createChild) just return the input File2
    val parentFile2 = mock[WrittenFile2]
    var nCreates = 0
    val createdFile2 = mock[CreatedFile2]
    val writtenFile2 = mock[WrittenFile2]
    val processedFile2 = mock[ProcessedFile2]

    val mockFile2Writer = mock[File2Writer]
    mockFile2Writer.createChild(any, any, any, any, any, any, any)(any) answers { _ =>
      createdFile2.blobOpt returns None
      createdFile2.indexInParent returns nCreates
      nCreates += 1
      Future.successful(createdFile2)
    }
    mockFile2Writer.deleteFile2(any)(any) returns Future.unit
    mockFile2Writer.writeBlob(any, any)(any, any) answers { _ =>
      createdFile2.blobOpt returns Some(BlobStorageRefWithSha1(BlobStorageRef("written", 10), Array(1, 2, 3).map(_.toByte)))
      Future.successful(createdFile2)
    }
    mockFile2Writer.writeBlobStorageRef(any, any)(any) answers { args =>
      createdFile2.blobOpt returns Some(args.asInstanceOf[Array[Any]](1).asInstanceOf[BlobStorageRefWithSha1])
      Future.successful(createdFile2)
    }
    mockFile2Writer.writeThumbnail(any, any, any)(any, any) returns Future.successful(createdFile2)
    mockFile2Writer.writeText(any, any)(any, any) returns Future.successful(createdFile2)
    mockFile2Writer.setWritten(any)(any) returns Future.successful(writtenFile2)
    mockFile2Writer.setProcessed(any, any, any)(any) returns Future.successful(processedFile2) // input is parentFile2

    def fragments: Vector[StepOutputFragment]

    val onProgressCalls = ArrayBuffer[Double]()
    val canceled = Promise[akka.Done]()

    val mockLogic = new StepLogic {
      override def processIntoFragments(
        file2: WrittenFile2,
        canceled: Future[akka.Done]
      )(implicit ec: ExecutionContext) = Source(fragments)
    }

    lazy val step = new Step(mockLogic, mockFile2Writer)

    val returnedParentPromise = Promise[ProcessedFile2]()

    lazy val source = step.process(parentFile2, onProgressCalls.+= _, canceled.future)
      .mapMaterializedValue(returnedParentPromise.completeWith _)

    lazy val running = source.runWith(Sink.seq[WrittenFile2])

    val emptyMetadata = File2.Metadata(JsObject(Seq()))
    val emptyPipelineOptions = File2.PipelineOptions(false, false)

    def result = await(for {
      children <- running // Run the queue
      parent <- returnedParentPromise.future
    } yield (parent, children))
  }

  "Step" should {
    "write a new File2" in new BaseScope {
      val blob0 = Source.single(ByteString("foo".getBytes))

      val metadata = File2.Metadata(JsObject(Seq("meta" -> JsString("data"))))
      val pipelineOptions = File2.PipelineOptions(true, false)

      override val fragments = Vector(
        StepOutputFragment.File2Header("foo", "text/csv", "en", metadata, pipelineOptions),
        StepOutputFragment.Blob(blob0),
        StepOutputFragment.Done
      )
      result must beEqualTo((processedFile2, Vector(writtenFile2)))
      there was one(mockFile2Writer).createChild(
        parentFile2,
        0,
        "foo",
        "text/csv",
        "en",
        metadata,
        pipelineOptions
      )
      there was one(mockFile2Writer).writeBlob(createdFile2, blob0)
      there was one(mockFile2Writer).setWritten(createdFile2)
      there was one(mockFile2Writer).setProcessed(parentFile2, 1, None)
    }

    "cancel immediately after start" in new BaseScope {
      override val fragments = Vector(StepOutputFragment.Canceled)
      result must beEqualTo((processedFile2, Vector()))
      there was no(mockFile2Writer).createChild(any, any, any, any, any, any, any)(any)
      there was one(mockFile2Writer).setProcessed(parentFile2, 0, Some("canceled"))
    }

    "allow empty output" in new BaseScope {
      override val fragments = Vector(StepOutputFragment.Done)
      result must beEqualTo((processedFile2, Vector()))
      there was no(mockFile2Writer).createChild(any, any, any, any, any, any, any)(any)
      there was one(mockFile2Writer).setProcessed(parentFile2, 0, None)
    }

    "delete partial output on cancel" in new BaseScope {
      override val fragments = Vector(
        StepOutputFragment.File2Header("foo", "text/csv", "en", emptyMetadata, emptyPipelineOptions),
        // createdFile2.indexInParent returns 0
        StepOutputFragment.Blob(Source.single(ByteString("foo".getBytes))),
        StepOutputFragment.Canceled
      )

      result must beEqualTo((processedFile2, Vector()))
      there was one(mockFile2Writer).deleteFile2(createdFile2)
      there was one(mockFile2Writer).setProcessed(parentFile2, 0, Some("canceled"))
    }

    "write multiple children" in new BaseScope {
      override val fragments = Vector(
        StepOutputFragment.File2Header("foo", "text/csv", "en", emptyMetadata, emptyPipelineOptions),
        StepOutputFragment.Blob(Source.single(ByteString("foo".getBytes))),
        StepOutputFragment.File2Header("bar", "text/csv", "en", emptyMetadata, emptyPipelineOptions),
        StepOutputFragment.Blob(Source.single(ByteString("bar".getBytes))),
        StepOutputFragment.Done
      )

      result must beEqualTo((processedFile2, Vector(writtenFile2, writtenFile2)))

      there was one(mockFile2Writer).createChild(parentFile2, 0, "foo", "text/csv", "en", emptyMetadata, emptyPipelineOptions)
      there was one(mockFile2Writer).createChild(parentFile2, 1, "bar", "text/csv", "en", emptyMetadata, emptyPipelineOptions)
      there was one(mockFile2Writer).setProcessed(parentFile2, 2, None)
    }

    "delete partial not-first-child on cancel" in new BaseScope {
      override val fragments = Vector(
        StepOutputFragment.File2Header("foo", "text/csv", "en", emptyMetadata, emptyPipelineOptions),
        StepOutputFragment.Blob(Source.single(ByteString("foo".getBytes))),
        StepOutputFragment.File2Header("bar", "text/csv", "en", emptyMetadata, emptyPipelineOptions),
        StepOutputFragment.Blob(Source.single(ByteString("bar".getBytes))),
        StepOutputFragment.Canceled
      )

      result must beEqualTo((processedFile2, Vector(writtenFile2)))

      there was one(mockFile2Writer).createChild(parentFile2, 0, "foo", "text/csv", "en", emptyMetadata, emptyPipelineOptions)
      there was one(mockFile2Writer).createChild(parentFile2, 1, "bar", "text/csv", "en", emptyMetadata, emptyPipelineOptions)
      there was one(mockFile2Writer).setProcessed(parentFile2, 1, Some("canceled"))
    }

    "write processingError=error on StepError" in new BaseScope {
      override val fragments = Vector(
        StepOutputFragment.File2Header("foo", "text/csv", "en", emptyMetadata, emptyPipelineOptions),
        StepOutputFragment.Blob(Source.single(ByteString("foo".getBytes))),
        StepOutputFragment.StepError(new Exception("foo"))
      )

      result must beEqualTo((processedFile2, Vector()))

      there was one(mockFile2Writer).setProcessed(parentFile2, 0, Some("step error: foo"))
    }

    "write thumbnail and text" in new BaseScope {
      val blob0 = Source.single(ByteString("foo".getBytes))
      val blob1 = Source.single(ByteString("bar".getBytes))

      override val fragments = Vector(
        StepOutputFragment.File2Header("foo", "text/csv", "en", emptyMetadata, emptyPipelineOptions),
        StepOutputFragment.Thumbnail("image/jpeg", blob0),
        StepOutputFragment.Text(blob1),
        StepOutputFragment.Canceled
      )

      result
      there was one(mockFile2Writer).writeThumbnail(createdFile2, "image/jpeg", blob0)
      there was one(mockFile2Writer).writeText(createdFile2, blob1)
    }

    "error when there is no blob at the end of the stream" in new BaseScope {
      override val fragments = Vector(
        StepOutputFragment.File2Header("foo", "text/csv", "en", emptyMetadata, emptyPipelineOptions),
        StepOutputFragment.Done
      )

      result
      there was one(mockFile2Writer).setProcessed(parentFile2, 0, Some("logic error: tried to write child without blob data"))
    }

    "error when there is no blob at the start of another file" in new BaseScope {
      override val fragments = Vector(
        StepOutputFragment.File2Header("foo", "text/csv", "en", emptyMetadata, emptyPipelineOptions),
        StepOutputFragment.File2Header("bar", "text/csv", "en", emptyMetadata, emptyPipelineOptions),
        StepOutputFragment.Done
      )

      result
      there was one(mockFile2Writer).setProcessed(parentFile2, 0, Some("logic error: unexpected fragment class com.overviewdocs.ingest.pipeline.StepOutputFragment$File2Header"))
    }

    "error when a blob comes without a file" in new BaseScope {
      override val fragments = Vector(
        StepOutputFragment.Blob(Source.single(ByteString("foo".getBytes))),
        StepOutputFragment.Done
      )

      result
      there was one(mockFile2Writer).setProcessed(parentFile2, 0, Some("logic error: unexpected fragment class com.overviewdocs.ingest.pipeline.StepOutputFragment$Blob"))
    }

    "allows inheriting a blob from the parent" in new BaseScope {
      val blobStorageRef = BlobStorageRefWithSha1(BlobStorageRef("foo", 10), Array(1, 2, 3).map(_.toByte))
      parentFile2.blob returns blobStorageRef

      override val fragments = Vector(
        StepOutputFragment.File2Header("foo", "text/csv", "en", emptyMetadata, emptyPipelineOptions),
        StepOutputFragment.InheritBlob,
        StepOutputFragment.Done
      )
      result
      there was one(mockFile2Writer).writeBlobStorageRef(createdFile2, blobStorageRef)
    }

    "ignore fragments after the end" in new BaseScope {
      override val fragments = Vector(
        StepOutputFragment.File2Header("foo", "text/csv", "en", emptyMetadata, emptyPipelineOptions),
        StepOutputFragment.InheritBlob,
        StepOutputFragment.Done,
        StepOutputFragment.File2Header("bar", "text/csv", "en", emptyMetadata, emptyPipelineOptions),
      )
      result must beEqualTo((processedFile2, Vector(writtenFile2)))
      there was no(mockFile2Writer).createChild(parentFile2, 1, "bar", "text/csv", "en", emptyMetadata, emptyPipelineOptions)
    }

    "report progress" in new BaseScope {
      override val fragments = Vector(
        StepOutputFragment.ProgressFraction(0.1),
        StepOutputFragment.Done
      )
      result
      onProgressCalls must beEqualTo(Vector(0.1))
    }
  }
}
