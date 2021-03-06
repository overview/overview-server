package com.overviewdocs.ingest.process

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow,Source}
import akka.util.ByteString
import play.api.libs.json.JsObject
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.ingest.model.{BlobStorageRefWithSha1,ConvertOutputElement,WrittenFile2,ResumedFileGroupJob,ProgressPiece}
import com.overviewdocs.models.BlobStorageRef
import com.overviewdocs.test.ActorSystemContext
import com.overviewdocs.util.AwaitMethod

class DeciderSpec extends Specification with Mockito {
  sequential

  trait BaseScope extends Scope with ActorSystemContext with AwaitMethod {
    implicit val ec: ExecutionContext = system.dispatcher

    val mockResumedFileGroupJob = mock[ResumedFileGroupJob]
    mockResumedFileGroupJob.isCanceled returns false

    def writtenFile2(
      filename: String,
      contentType: String,
      ocr: Boolean,
      stepsRemaining: Vector[String]
    ) = WrittenFile2(
      0L,
      mockResumedFileGroupJob,
      mock[ProgressPiece],
      None,
      None,
      filename,
      contentType,
      "en",
      JsObject(Seq()),
      ocr,
      false,
      BlobStorageRefWithSha1(BlobStorageRef("foo", 10L), "abc".getBytes)
    )

    val mockBlobStorage = mock[BlobStorage]
    case class MockStep(override val id: String) extends Step {
      override val progressWeight: Double = 1.0
      override val flow = Flow[WrittenFile2]
        .collect(PartialFunction.empty[WrittenFile2,ConvertOutputElement])
        .mapMaterializedValue { _ =>
          import akka.http.scaladsl.server.{RequestContext,RouteResult}
          (ctx: RequestContext) => Future.successful(RouteResult.Rejected(Nil))
        }
    }
    val steps = Vector("PdfOcr", "Pdf", "Pst", "Office", "Archive", "Email", "Html", "Image", "Text", "Unhandled", "Canceled").map(MockStep.apply _)
    val decider = new Decider(steps, mockBlobStorage)
  }

  "Decider" should {
    "#getContentType" should {
      "return the content type, when provided" in new BaseScope {
        val input = writtenFile2("foo.doc", "text/csv", false, Vector())
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
        mockBlobStorage.getBytes(any, any) returns Future.successful(
          Array(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a).map(_.toByte)
        )
        val input = writtenFile2("foo.blob", "application/octet-stream", false, Vector())
        await(decider.getContentTypeNoParameters(input)) must beEqualTo("image/png")
      }
    }

    "#getStepsRemaining" should {
      "detect Office steps" in new BaseScope {
        val input = writtenFile2("file.docx", "application/octet-stream", false, Vector())
        await(decider.getNextStep(input).map(_.id)) must beEqualTo("Office")
      }

      "detect Image steps" in new BaseScope {
        val input = writtenFile2("file.jpg", "application/octet-stream", false, Vector())
        await(decider.getNextStep(input).map(_.id)) must beEqualTo("Image")
      }

      "detect Email steps" in new BaseScope {
        val input = writtenFile2("input.pst/Folder/3", "message/rfc822", false, Vector())
        await(decider.getNextStep(input).map(_.id)) must beEqualTo("Email")
      }

      "send .csv to Office (because it's a spreadsheet)" in new BaseScope {
        val input = writtenFile2("file.csv", "application/octet-stream", false, Vector())
        await(decider.getNextStep(input).map(_.id)) must beEqualTo("Office")
      }

      "send HTML to HTML" in new BaseScope {
        val input = writtenFile2("file.html", "application/octet-stream", false, Vector())
        await(decider.getNextStep(input).map(_.id)) must beEqualTo("Html")
      }

      "send RTF to HTML" in new BaseScope {
        val input = writtenFile2("file.rtf", "application/octet-stream", false, Vector())
        await(decider.getNextStep(input).map(_.id)) must beEqualTo("Html")
      }

      "send PST to HTML" in new BaseScope {
        val input = writtenFile2("file.pst", "application/octet-stream", false, Vector())
        await(decider.getNextStep(input).map(_.id)) must beEqualTo("Pst")
      }

      "detect Pdf steps with OCR" in new BaseScope {
        val input = writtenFile2("file.pdf", "application/octet-stream", true, Vector())
        await(decider.getNextStep(input).map(_.id)) must beEqualTo("PdfOcr")
      }

      "skip OCR when option is disabled" in new BaseScope {
        val input = writtenFile2("file.pdf", "application/octet-stream", false, Vector())
        await(decider.getNextStep(input).map(_.id)) must beEqualTo("Pdf")
      }

      "do Text for text/*, where * is something we don't handle explicitly" in new BaseScope {
        val input = writtenFile2("image.ascii", "text/vnd.ascii-art", false, Vector())
        await(decider.getNextStep(input).map(_.id)) must beEqualTo("Text")
      }

      "do Text for shell scripts" in new BaseScope {
        mockBlobStorage.getBytes(any, any) returns Future.successful("#!/bin/sh\n\necho 'foo'\n".getBytes("utf-8"));
        val input = writtenFile2("script.sh", "application/octet-stream", false, Vector())
        await(decider.getNextStep(input).map(_.id)) must beEqualTo("Text")
      }

      "default to Unhandled" in new BaseScope {
        val input = writtenFile2("weird.3mf", "model/3mf", false, Vector())
        await(decider.getNextStep(input).map(_.id)) must beEqualTo("Unhandled")
      }

      "choose Canceled when job.isCanceled" in new BaseScope {
        val input = writtenFile2("weird.3mf", "model/3mf", false, Vector())
        mockResumedFileGroupJob.isCanceled returns true
        await(decider.getNextStep(input).map(_.id)) must beEqualTo("Canceled")
      }
    }
  }
}
