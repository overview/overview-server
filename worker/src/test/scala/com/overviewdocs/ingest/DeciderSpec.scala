package com.overviewdocs.ingest

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow,Source}
import akka.util.ByteString
import play.api.libs.json.JsObject
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.ingest.models.{BlobStorageRefWithSha1,ConvertOutputElement,WrittenFile2,ResumedFileGroupJob}
import com.overviewdocs.ingest.pipeline.Step
import com.overviewdocs.models.{BlobStorageRef,File2}
import com.overviewdocs.test.ActorSystemContext
import com.overviewdocs.util.AwaitMethod

class DeciderSpec extends Specification with Mockito {
  sequential

  trait BaseScope extends Scope with ActorSystemContext with AwaitMethod {
    implicit val ec: ExecutionContext = system.dispatcher

    def writtenFile2(
      filename: String,
      contentType: String,
      ocr: Boolean,
      stepsRemaining: Vector[String]
    ) = WrittenFile2(
      0L,
      mock[ResumedFileGroupJob],
      _ => (),
      None,
      None,
      filename,
      contentType,
      "en",
      File2.Metadata(JsObject(Seq())),
      File2.PipelineOptions(ocr, false, stepsRemaining),
      BlobStorageRefWithSha1(BlobStorageRef("foo", 10), "abc".getBytes)
    )

    val mockBlobStorage = mock[BlobStorage]
    case class MockStep(override val id: String) extends Step {
      override val flow = Flow[WrittenFile2]
        .collect(PartialFunction.empty[WrittenFile2,ConvertOutputElement])
        .mapMaterializedValue { _ =>
          import akka.http.scaladsl.server.{RequestContext,RouteResult}
          (ctx: RequestContext) => Future.successful(RouteResult.Rejected(Nil))
        }
    }
    val steps = Vector("Ocr", "SplitExtract", "Office", "Archive", "Unhandled").map(MockStep.apply _)
    val decider = new Decider(steps, mockBlobStorage)
  }

  "Decider" should {
    "#getContentType" should {
      "return the content type, normally" in new BaseScope {
        val input = writtenFile2("foo.csv", "text/csv", false, Vector())
        await(decider.getContentTypeNoParameters(input)) must beEqualTo("text/csv")
      }

      "nix parameters from content type" in new BaseScope {
        val input = writtenFile2("foo.csv", "text/csv; charset=utf-8", false, Vector())
        await(decider.getContentTypeNoParameters(input)) must beEqualTo("text/csv")
      }

      "detect using filename" in new BaseScope {
        val input = writtenFile2("foo.pdf", "application/octet-stream", false, Vector())
        await(decider.getContentTypeNoParameters(input)) must beEqualTo("application/pdf")
      }

      "detect using magic number" in new BaseScope {
        mockBlobStorage.get(any) returns Source(Vector(
          ByteString(Array(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a).map(_.toByte))
        ))
        val input = writtenFile2("foo.blob", "application/octet-stream", false, Vector())
        await(decider.getContentTypeNoParameters(input)) must beEqualTo("image/png")
      }

      "handle large blobs" in new BaseScope {
        // We _assume_ the getBytes function truncates. We're only really
        // testing that the truncation code doesn't _crash_.
        mockBlobStorage.get(any) returns Source(Vector(
          ByteString(Array(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a).map(_.toByte)),
          ByteString(Array.fill(Decider.mimeTypeDetector.getMaxGetBytesLength)(0x0a.toByte)),
          ByteString(Array.fill(Decider.mimeTypeDetector.getMaxGetBytesLength)(0x0a.toByte)),
          ByteString(Array.fill(Decider.mimeTypeDetector.getMaxGetBytesLength)(0x0a.toByte))
        ))
        val input = writtenFile2("foo.blob", "application/octet-stream", false, Vector())
        await(decider.getContentTypeNoParameters(input)) must beEqualTo("image/png")
      }
    }

    "#getStepsRemaining" should {
      "detect Office steps" in new BaseScope {
        val input = writtenFile2("file.docx", "application/octet-stream", false, Vector())
        await(decider.ensurePipelineStepsRemaining(input)).pipelineOptions.stepsRemaining must beEqualTo(Vector("Office", "SplitExtract"))
      }

      "no-op when input has pipeline steps remaining" in new BaseScope {
        val input = writtenFile2("file.pdf", "application/pdf", false, Vector("SplitExtract"))
        await(decider.ensurePipelineStepsRemaining(input)) must beEqualTo(input)
      }

      "detect Pdf steps with OCR" in new BaseScope {
        val input = writtenFile2("file.pdf", "application/octet-stream", true, Vector())
        await(decider.ensurePipelineStepsRemaining(input)).pipelineOptions.stepsRemaining must beEqualTo(Vector("Ocr", "SplitExtract"))
      }

      "skip OCR when option is disabled" in new BaseScope {
        val input = writtenFile2("file.pdf", "application/octet-stream", false, Vector())
        await(decider.ensurePipelineStepsRemaining(input)).pipelineOptions.stepsRemaining must beEqualTo(Vector("SplitExtract"))
      }

      "do something for text/*, where * is something we don't handle explicitly" in new BaseScope {
        val input = writtenFile2("image.ascii", "text/vnd.ascii-art", false, Vector())
        await(decider.ensurePipelineStepsRemaining(input)).pipelineOptions.stepsRemaining must not(beEqualTo(Vector("Unhandled")))
      }

      "default to Unhandled" in new BaseScope {
        val input = writtenFile2("weird.3mf", "model/3mf", false, Vector())
        await(decider.ensurePipelineStepsRemaining(input)).pipelineOptions.stepsRemaining must beEqualTo(Vector("Unhandled"))
      }
    }
  }
}
