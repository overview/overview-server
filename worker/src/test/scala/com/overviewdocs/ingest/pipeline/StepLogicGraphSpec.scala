package com.overviewdocs.ingest.pipeline

import akka.stream.{ActorMaterializer,ClosedShape,Materializer}
import akka.stream.scaladsl.{GraphDSL,Keep,RunnableGraph,Sink,Source}
import akka.util.ByteString
import org.mockito.invocation.InvocationOnMock
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.{Json,JsObject,JsString}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext,Future,Promise,blocking}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.ingest.models.{BlobStorageRefWithSha1,ConvertOutputElement,CreatedFile2,WrittenFile2,ProcessedFile2}
import com.overviewdocs.models.{BlobStorageRef,File2}
import com.overviewdocs.test.ActorSystemContext

class StepLogicFlowSpec extends Specification with Mockito {
  sequential

  class MockStepLogic(fragments: Vector[StepOutputFragment]) extends StepLogic {
    override def toChildFragments(
      blobStorage: BlobStorage,
      file2: WrittenFile2,
    )(implicit ec: ExecutionContext, mat: Materializer) = Source(fragments)
  }

  protected def await[T](future: Future[T]): T = {
    blocking(scala.concurrent.Await.result(future, scala.concurrent.duration.Duration("2s")))
  }

  trait BaseScope extends Scope with ActorSystemContext {
    implicit val ec = system.dispatcher

    // Mock File2Writer: all methods (except createChild) just return the input File2
    val onProgressCalls = ArrayBuffer[Double]()
    val parentFile2 = mock[WrittenFile2]
    parentFile2.onProgress returns onProgressCalls.+= _
    // Stuff for logger
    parentFile2.id returns 1L
    parentFile2.filename returns "filename.blob"
    parentFile2.blob returns BlobStorageRefWithSha1(BlobStorageRef("loc:parent", 10), Array.empty[Byte])
    parentFile2.pipelineOptions returns File2.PipelineOptions(false, false, Vector("foo"))
    var nCreates = 0
    val createdFile2 = mock[CreatedFile2]
    val writtenFile2 = mock[WrittenFile2]
    val processedFile2 = mock[ProcessedFile2]
    val processedParent = mock[ProcessedFile2]

    val mockFile2Writer = mock[File2Writer]
    mockFile2Writer.createChild(any, any, any, any, any, any, any)(any) answers { args =>
      createdFile2.blobOpt returns None
      createdFile2.indexInParent returns nCreates
      createdFile2.contentType returns args.asInstanceOf[Array[Any]](3).asInstanceOf[String]
      createdFile2.pipelineOptions returns args.asInstanceOf[Array[Any]](6).asInstanceOf[File2.PipelineOptions]
      nCreates += 1
      Future.successful(createdFile2)
    }
    mockFile2Writer.delete(any)(any) returns Future.unit
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
    mockFile2Writer.setWrittenAndProcessed(any)(any) returns Future.successful(processedFile2)
    mockFile2Writer.setProcessed(any, any, any)(any) returns Future.successful(processedParent) // input is parentFile2

    val fragments: Vector[StepOutputFragment]
    lazy val mockLogic = new MockStepLogic(fragments)

    val returnedParentPromise = Promise[ProcessedFile2]()

    val source = Source.single(parentFile2)
    lazy val flow = new StepLogicFlow(mockLogic, mockFile2Writer, 2).flow

    lazy val resultFuture: Future[(Vector[WrittenFile2], Vector[ProcessedFile2])] = {
      for {
        output <- source.via(flow).runWith(Sink.seq[ConvertOutputElement])
      } yield (
        // Split WrittenFile2s and ProcessedFile2s
        output.collect { case ConvertOutputElement.ToProcess(w) => w }.to[Vector],
        output.collect { case ConvertOutputElement.ToIngest(p) => p }.to[Vector]
      )
    }

    lazy val result = await(resultFuture)

    val emptyMetadata = File2.Metadata(JsObject(Seq()))
    val defaultPipelineOptions = File2.PipelineOptions(false, false, Vector("foo", "bar"))
  }

  "Step" should {
    "write a new File2" in new BaseScope {
      val blob0 = Source.single(ByteString("foo".getBytes))

      val metadata = File2.Metadata(JsObject(Seq("meta" -> JsString("data"))))
      val pipelineOptions = File2.PipelineOptions(true, false, Vector("foo"))

      override val fragments = Vector(
        StepOutputFragment.File2Header(0, "foo", "text/csv", "en", metadata, pipelineOptions),
        StepOutputFragment.Blob(0, blob0),
        StepOutputFragment.Done
      )
      result must beEqualTo((Vector(writtenFile2), Vector(processedParent)))
      there was one(mockFile2Writer).createChild(
        parentFile2,
        0,
        "foo",
        "text/csv",
        "en",
        metadata,
        File2.PipelineOptions(true, false, Vector("foo"))
      )
      there was one(mockFile2Writer).writeBlob(createdFile2, blob0)
      there was one(mockFile2Writer).setWritten(createdFile2)
      there was one(mockFile2Writer).setProcessed(parentFile2, 1, None)
    }

    "write a ProcessedFile2 if there are no more processing steps" in new BaseScope {
      val blob0 = Source.single(ByteString("foo".getBytes))

      val metadata = File2.Metadata(JsObject(Seq("meta" -> JsString("data"))))
      val pipelineOptions = File2.PipelineOptions(true, false, Vector())

      override val fragments = Vector(
        StepOutputFragment.File2Header(0, "foo", "application/pdf", "en", metadata, pipelineOptions),
        StepOutputFragment.Blob(0, blob0),
        StepOutputFragment.Done
      )
      result must beEqualTo((Vector(), Vector(processedFile2, processedParent)))
      there was one(mockFile2Writer).setWrittenAndProcessed(createdFile2)
    }

    "write a WrittenFile2 if there are no more processing steps but we are meant to recurse" in new BaseScope {
      val blob0 = Source.single(ByteString("foo".getBytes))

      val metadata = File2.Metadata(JsObject(Seq("meta" -> JsString("data"))))
      val pipelineOptions = File2.PipelineOptions(true, false, Vector())

      override val fragments = Vector(
        StepOutputFragment.File2Header(0, "foo", "application/anything-but-pdf", "en", metadata, pipelineOptions),
        StepOutputFragment.Blob(0, blob0),
        StepOutputFragment.Done
      )
      result must beEqualTo((Vector(writtenFile2), Vector(processedParent)))
      there was one(mockFile2Writer).setWritten(createdFile2)
      there was no(mockFile2Writer).setWrittenAndProcessed(any)(any)
    }

    "cancel immediately after start" in new BaseScope {
      override val fragments = Vector(StepOutputFragment.Canceled)
      result must beEqualTo((Vector(), Vector(processedParent)))
      there was no(mockFile2Writer).createChild(any, any, any, any, any, any, any)(any)
      there was one(mockFile2Writer).setProcessed(parentFile2, 0, Some("canceled"))
    }

    "allow empty output" in new BaseScope {
      override val fragments = Vector(StepOutputFragment.Done)
      result must beEqualTo((Vector(), Vector(processedParent)))
      there was no(mockFile2Writer).createChild(any, any, any, any, any, any, any)(any)
      there was one(mockFile2Writer).setProcessed(parentFile2, 0, None)
    }

    "delete partial output on cancel" in new BaseScope {
      override val fragments = Vector(
        StepOutputFragment.File2Header(0, "foo", "text/csv", "en", emptyMetadata, defaultPipelineOptions),
        // createdFile2.indexInParent returns 0
        StepOutputFragment.Blob(0, Source.single(ByteString("foo".getBytes))),
        StepOutputFragment.Canceled
      )

      result must beEqualTo((Vector(), Vector(processedParent)))
      there was one(mockFile2Writer).delete(createdFile2)
      there was one(mockFile2Writer).setProcessed(parentFile2, 0, Some("canceled"))
    }

    "write multiple children" in new BaseScope {
      override val fragments = Vector(
        StepOutputFragment.File2Header(0, "foo", "text/csv", "en", emptyMetadata, defaultPipelineOptions),
        StepOutputFragment.Blob(0, Source.single(ByteString("foo".getBytes))),
        StepOutputFragment.File2Header(1, "bar", "text/csv", "en", emptyMetadata, defaultPipelineOptions),
        StepOutputFragment.Blob(1, Source.single(ByteString("bar".getBytes))),
        StepOutputFragment.Done
      )

      result must beEqualTo((Vector(writtenFile2, writtenFile2), Vector(processedParent)))

      there was one(mockFile2Writer).createChild(parentFile2, 0, "foo", "text/csv", "en", emptyMetadata, defaultPipelineOptions)
      there was one(mockFile2Writer).createChild(parentFile2, 1, "bar", "text/csv", "en", emptyMetadata, defaultPipelineOptions)
      there was one(mockFile2Writer).setProcessed(parentFile2, 2, None)
    }

    "delete partial not-first-child on cancel" in new BaseScope {
      override val fragments = Vector(
        StepOutputFragment.File2Header(0, "foo", "text/csv", "en", emptyMetadata, defaultPipelineOptions),
        StepOutputFragment.Blob(0, Source.single(ByteString("foo".getBytes))),
        StepOutputFragment.File2Header(1, "bar", "text/csv", "en", emptyMetadata, defaultPipelineOptions),
        StepOutputFragment.Blob(1, Source.single(ByteString("bar".getBytes))),
        StepOutputFragment.Canceled
      )

      result must beEqualTo((Vector(writtenFile2), Vector(processedParent)))

      there was one(mockFile2Writer).createChild(parentFile2, 0, "foo", "text/csv", "en", emptyMetadata, defaultPipelineOptions)
      there was one(mockFile2Writer).createChild(parentFile2, 1, "bar", "text/csv", "en", emptyMetadata, defaultPipelineOptions)
      there was one(mockFile2Writer).setProcessed(parentFile2, 1, Some("canceled"))
    }

    "write processingError=error on StepError" in new BaseScope {
      override val fragments = Vector(
        StepOutputFragment.File2Header(0, "foo", "text/csv", "en", emptyMetadata, defaultPipelineOptions),
        StepOutputFragment.Blob(0, Source.single(ByteString("foo".getBytes))),
        StepOutputFragment.StepError(new Exception("foo"))
      )

      result must beEqualTo((Vector(), Vector(processedParent)))

      there was one(mockFile2Writer).setProcessed(parentFile2, 0, Some("step error: foo"))
    }

    "write thumbnail and text" in new BaseScope {
      val blob0 = Source.single(ByteString("foo".getBytes))
      val blob1 = Source.single(ByteString("bar".getBytes))

      override val fragments = Vector(
        StepOutputFragment.File2Header(0, "foo", "text/csv", "en", emptyMetadata, defaultPipelineOptions),
        StepOutputFragment.Thumbnail(0, "image/jpeg", blob0),
        StepOutputFragment.Text(0, blob1),
        StepOutputFragment.Canceled
      )

      result
      there was one(mockFile2Writer).writeThumbnail(createdFile2, "image/jpeg", blob0)
      there was one(mockFile2Writer).writeText(createdFile2, blob1)
    }

    "error when there is no blob at the end of the stream" in new BaseScope {
      override val fragments = Vector(
        StepOutputFragment.File2Header(0, "foo", "text/csv", "en", emptyMetadata, defaultPipelineOptions),
        StepOutputFragment.Done
      )

      result
      there was one(mockFile2Writer).setProcessed(parentFile2, 0, Some("logic error in com.overviewdocs.ingest.pipeline.StepLogicFlowSpec$MockStepLogic: tried to write child without blob data"))
    }

    "error when there is no blob at the start of another file" in new BaseScope {
      override val fragments = Vector(
        StepOutputFragment.File2Header(0, "foo", "text/csv", "en", emptyMetadata, defaultPipelineOptions),
        StepOutputFragment.File2Header(1, "bar", "text/csv", "en", emptyMetadata, defaultPipelineOptions),
        StepOutputFragment.Done
      )

      result
      there was one(mockFile2Writer).setProcessed(parentFile2, 0, Some("logic error in com.overviewdocs.ingest.pipeline.StepLogicFlowSpec$MockStepLogic: tried to write child without blob data"))
    }

    "error when a blob comes without a file" in new BaseScope {
      override val fragments = Vector(
        StepOutputFragment.Blob(0, Source.single(ByteString("foo".getBytes))),
        StepOutputFragment.Done
      )

      result
      there was one(mockFile2Writer).setProcessed(parentFile2, 0, Some("logic error in com.overviewdocs.ingest.pipeline.StepLogicFlowSpec$MockStepLogic: unexpected fragment class com.overviewdocs.ingest.pipeline.StepOutputFragment$Blob"))
    }

    "allows inheriting a blob from the parent" in new BaseScope {
      val blobStorageRef = BlobStorageRefWithSha1(BlobStorageRef("foo", 10), Array(1, 2, 3).map(_.toByte))
      parentFile2.blob returns blobStorageRef

      override val fragments = Vector(
        StepOutputFragment.File2Header(0, "foo", "text/csv", "en", emptyMetadata, defaultPipelineOptions),
        StepOutputFragment.InheritBlob,
        StepOutputFragment.Done
      )
      result
      there was one(mockFile2Writer).writeBlobStorageRef(createdFile2, blobStorageRef)
    }

    "ignore fragments after the end" in new BaseScope {
      override val fragments = Vector(
        StepOutputFragment.File2Header(0, "foo", "text/csv", "en", emptyMetadata, defaultPipelineOptions),
        StepOutputFragment.InheritBlob,
        StepOutputFragment.Done,
        StepOutputFragment.File2Header(1, "bar", "text/csv", "en", emptyMetadata, defaultPipelineOptions),
      )
      result must beEqualTo((Vector(writtenFile2), Vector(processedParent)))
      there was no(mockFile2Writer).createChild(parentFile2, 1, "bar", "text/csv", "en", emptyMetadata, defaultPipelineOptions)
    }

    "ignore spurious fragments (e.g., from duplicated workers)" in new BaseScope {
      val blob = Source.single(ByteString("blob".getBytes))
      val blob2 = Source.single(ByteString("blob2".getBytes))
      val blob3 = Source.single(ByteString("blob3".getBytes))

      override val fragments = Vector(
        StepOutputFragment.File2Header(0, "foo", "text/csv", "en", emptyMetadata, defaultPipelineOptions),
        StepOutputFragment.Blob(0, blob),
        StepOutputFragment.Thumbnail(0, "image/png", blob),
        StepOutputFragment.Text(0, blob),
        StepOutputFragment.File2Header(1, "bar", "text/csv", "en", emptyMetadata, defaultPipelineOptions),
        StepOutputFragment.File2Header(0, "bar", "text/csv", "en", emptyMetadata, defaultPipelineOptions),
        StepOutputFragment.Blob(0, blob2),
        StepOutputFragment.Thumbnail(0, "image/jpeg", blob2),
        StepOutputFragment.Text(0, blob2),
        StepOutputFragment.Text(1, blob3),
        StepOutputFragment.Done
      )

      result
      there was no(mockFile2Writer).createChild(parentFile2, 0, "bar", "text/csv", "en", emptyMetadata, defaultPipelineOptions)
      there was no(mockFile2Writer).writeBlob(createdFile2, blob2)
      there was no(mockFile2Writer).writeThumbnail(createdFile2, "image/jpeg", blob2)
      there was no(mockFile2Writer).writeText(createdFile2, blob2)
      there was one(mockFile2Writer).createChild(parentFile2, 1, "bar", "text/csv", "en", emptyMetadata, defaultPipelineOptions)
      there was one(mockFile2Writer).writeText(createdFile2, blob3)
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
