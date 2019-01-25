package com.overviewdocs.ingest.process

import akka.stream.{Graph,Materializer,Outlet,UniformFanOutShape}
import akka.stream.scaladsl.{Flow,GraphDSL,Partition,Sink}
import akka.util.ByteString
import java.util.concurrent.Callable
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.ingest.model.WrittenFile2
import com.overviewdocs.models.BlobStorageRef
import com.overviewdocs.util.Logger
import org.overviewproject.mime_types.MimeTypeDetector

/** Ensures a valid `contentType` for each input WrittenFile2; emits to the
  * appropriate Step.
  *
  * Some Steps have special considerations:
  *
  * * "Canceled" is selected if the job is canceled
  * * "Unhandled" is the default Step
  * * "PdfOcr" is selected over "Pdf" if wantOcr is true
  *
  * This Graph has `steps.size` outlets: one per (hard-coded) Step. Outputs
  * on outlet 0 should connect to `Step.all(0)`'s inlet. Outputs on outlet 1
  * should connect to `Step.all(1)`'s inlet. And so on.
  *
  * We don't write our results to the database: we assume contentType
  * detection is relatively quick. Most of the time, it relies on filename
  * extension. Only rarely does it use magic numbers from BlobStorage. Plus,
  * if there's a large backlog of magic-number detection to handle, that means
  * downstream isn't processing files quickly enough.
  */
class Decider(
  steps: Vector[Step],
  blobStorage: BlobStorage,

  /** Number of magic-number detections to run in parallel.
    *
    * Usually, the slow part is reading from BlobStorage.
    */
  parallelism: Int = 2
) {
  private val logger = Logger.forClass(getClass)

  sealed trait NextStep {
    def forFile(file: WrittenFile2): Step
  }
  object NextStep {
    case class SimpleStep(stepId: String) extends NextStep {
      val step: Step = steps.find(_.id == stepId).get
      override def forFile(file: WrittenFile2) = step
    }

    case object PdfStep extends NextStep {
      val ocrStep = steps.find(_.id == "PdfOcr").get
      val noOcrStep = steps.find(_.id == "Pdf").get

      override def forFile(file: WrittenFile2) = {
        if (file.wantOcr) {
          ocrStep
        } else {
          noOcrStep
        }
      }
    }

    val Archive = SimpleStep("Archive")
    val Email = SimpleStep("Email")
    val Html = SimpleStep("Html")
    val Image = SimpleStep("Image")
    val Office = SimpleStep("Office")
    val Pdf = PdfStep
    val Pst = SimpleStep("Pst")
    val Text = SimpleStep("Text")

    val Canceled = SimpleStep("Canceled")
    val Unhandled = SimpleStep("Unhandled")
  }

  private val handlers = Map[String,NextStep](
    "application/pdf" -> NextStep.Pdf,

    "application/bzip2" -> NextStep.Archive,
    "application/gzip" -> NextStep.Archive,
    "application/jar" -> NextStep.Archive,
    "application/tar" -> NextStep.Archive,
    "application/vnd.ms-cab-compressed" -> NextStep.Archive,
    "application/x-7z-compressed" -> NextStep.Archive,
    "application/x-bzip2" -> NextStep.Archive,
    "application/x-bzip2-compressed-tar" -> NextStep.Archive,
    "application/x-bzip" -> NextStep.Archive,
    "application/x-bzip-compressed-tar" -> NextStep.Archive,
    "application/x-compressed-tar" -> NextStep.Archive,
    "application/x-iso9660-image" -> NextStep.Archive,
    "application/x-rar-compressed" -> NextStep.Archive,
    "application/x-tar" -> NextStep.Archive,
    "application/x-xz" -> NextStep.Archive,
    "application/x-xz-compressed-tar" -> NextStep.Archive,
    "application/zip" -> NextStep.Archive,

    "message/rfc822" -> NextStep.Email,

    "image/jpeg" -> NextStep.Image,
    "image/png" -> NextStep.Image,

    "application/clarisworks" -> NextStep.Office,
    "application/excel" -> NextStep.Office,
    "application/macwriteii" -> NextStep.Office,
    "application/msexcel" -> NextStep.Office,
    "application/mspowerpoint" -> NextStep.Office,
    "application/msword" -> NextStep.Office,
    "application/prs.plucker" -> NextStep.Office,
    "application/tab-separated-values" -> NextStep.Office,
    "application/vnd.corel-draw" -> NextStep.Office,
    "application/vnd.lotus-1-2-3" -> NextStep.Office,
    "application/vnd.lotus-wordpro" -> NextStep.Office,
    "application/vnd.ms-excel" -> NextStep.Office,
    "application/vnd.ms-excel.sheet.binary.macroenabled.12" -> NextStep.Office,
    "application/vnd.ms-excel.sheet.macroenabled.12" -> NextStep.Office,
    "application/vnd.ms-excel.template.macroenabled.12" -> NextStep.Office,
    "application/vnd.ms-powerpoint" -> NextStep.Office,
    "application/vnd.ms-powerpoint.presentation.macroenabled.12" -> NextStep.Office,
    "application/vnd.ms-powerpoint.slideshow.macroEnabled.12" -> NextStep.Office,
    "application/vnd.ms-powerpoint.template.macroenabled.12" -> NextStep.Office,
    "application/vnd.ms-publisher" -> NextStep.Office,
    "application/vnd.ms-word" -> NextStep.Office,
    "application/vnd.ms-word.document.macroenabled.12" -> NextStep.Office,
    "application/vnd.ms-word.template.macroenabled.12" -> NextStep.Office,
    "application/vnd.ms-works" -> NextStep.Office,
    "application/vnd.oasis.opendocument.chart" -> NextStep.Office,
    "application/vnd.oasis.opendocument.chart-template" -> NextStep.Office,
    "application/vnd.oasis.opendocument.graphics" -> NextStep.Office,
    "application/vnd.oasis.opendocument.graphics-flat-xml" -> NextStep.Office,
    "application/vnd.oasis.opendocument.graphics-template" -> NextStep.Office,
    "application/vnd.oasis.opendocument.presentation" -> NextStep.Office,
    "application/vnd.oasis.opendocument.presentation-flat-xml" -> NextStep.Office,
    "application/vnd.oasis.opendocument.presentation-template" -> NextStep.Office,
    "application/vnd.oasis.opendocument.spreadsheet" -> NextStep.Office,
    "application/vnd.oasis.opendocument.spreadsheet-flat-xml" -> NextStep.Office,
    "application/vnd.oasis.opendocument.spreadsheet-template" -> NextStep.Office,
    "application/vnd.oasis.opendocument.text" -> NextStep.Office,
    "application/vnd.oasis.opendocument.text-flat-xml" -> NextStep.Office,
    "application/vnd.oasis.opendocument.text-master" -> NextStep.Office,
    "application/vnd.oasis.opendocument.text-template" -> NextStep.Office,
    "application/vnd.oasis.opendocument.text-web" -> NextStep.Office,
    "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> NextStep.Office,
    "application/vnd.openxmlformats-officedocument.presentationml.slide" -> NextStep.Office,
    "application/vnd.openxmlformats-officedocument.presentationml.slideshow" -> NextStep.Office,
    "application/vnd.openxmlformats-officedocument.presentationml.template" -> NextStep.Office,
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> NextStep.Office,
    "application/vnd.openxmlformats-officedocument.spreadsheetml.template" -> NextStep.Office,
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> NextStep.Office,
    "application/vnd.openxmlformats-officedocument.wordprocessingml.template" -> NextStep.Office,
    "application/vnd.palm" -> NextStep.Office,
    "application/vnd.stardivision.writer-global" -> NextStep.Office,
    "application/vnd.sun.xml.calc" -> NextStep.Office,
    "application/vnd.sun.xml.calc.template" -> NextStep.Office,
    "application/vnd.sun.xml.draw" -> NextStep.Office,
    "application/vnd.sun.xml.draw.template" -> NextStep.Office,
    "application/vnd.sun.xml.impress" -> NextStep.Office,
    "application/vnd.sun.xml.impress.template" -> NextStep.Office,
    "application/vnd.sun.xml.writer" -> NextStep.Office,
    "application/vnd.sun.xml.writer.global" -> NextStep.Office,
    "application/vnd.sun.xml.writer.template" -> NextStep.Office,
    "application/vnd.visio" -> NextStep.Office,
    "application/vnd.wordperfect" -> NextStep.Office,
    "application/wordperfect" -> NextStep.Office,
    "application/x-123" -> NextStep.Office,
    "application/x-aportisdoc" -> NextStep.Office,
    "application/x-dbase" -> NextStep.Office,
    "application/x-dbf" -> NextStep.Office,
    "application/x-doc" -> NextStep.Office,
    "application/x-dos_ms_excel" -> NextStep.Office,
    "application/x-excel" -> NextStep.Office,
    "application/x-extension-txt" -> NextStep.Office,
    "application/x-fictionbook+xml" -> NextStep.Office,
    "application/x-hwp" -> NextStep.Office,
    "application/x-iwork-keynote-sffkey" -> NextStep.Office,
    "application/x-msexcel" -> NextStep.Office,
    "application/x-ms-excel" -> NextStep.Office,
    "application/x-quattropro" -> NextStep.Office,
    "application/x-t602" -> NextStep.Office,
    "application/x-wpg" -> NextStep.Office,
    "image/x-freehand" -> NextStep.Office,

    // One can imagine better than LibreOffice for CSV
    "application/csv" -> NextStep.Office,
    "text/csv" -> NextStep.Office,

    "application/vnd.ms-outlook" -> NextStep.Pst,

    "application/rtf" -> NextStep.Html,
    "text/html" -> NextStep.Html,
    "application/xhtml+xml" -> NextStep.Html,

    "application/javascript" -> NextStep.Text,
    "application/json" -> NextStep.Text,
    "application/x-python" -> NextStep.Text,
    "application/x-ruby" -> NextStep.Text,
    "application/x-shellscript" -> NextStep.Text,
    "application/x-yaml" -> NextStep.Text,
    "application/xml" -> NextStep.Text,
    "text/*" -> NextStep.Text // Lots of source code in this category
  )

  def graph(implicit mat: Materializer): Graph[UniformFanOutShape[WrittenFile2, WrittenFile2], akka.NotUsed] = {
    implicit val ec = mat.executionContext

    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val addOutletIndex = builder.add(Flow.apply[WrittenFile2].mapAsyncUnordered(parallelism) { file =>
        getOutletIndex(file).map(i => (file, i))
      })

      val partition = builder.add(Partition[(WrittenFile2,Int)](steps.size, t => t._2))
      val extractFiles = steps.map { _ => builder.add(Flow.apply[(WrittenFile2,Int)].map(t => t._1)) }

      addOutletIndex ~> partition
      extractFiles.foreach { extractFile =>
                        partition ~> extractFile
      }

      // The compiler seems to need help typecasting outlets
      val outlets: Seq[Outlet[WrittenFile2]] = extractFiles.map(_.out)
      UniformFanOutShape(addOutletIndex.in, outlets: _*)
    }
  }

  private def getBytes(
    blobLocation: String
  )(implicit mat: Materializer): Future[Array[Byte]] = {
    val maxNBytes = Decider.mimeTypeDetector.getMaxGetBytesLength
    blobStorage.getBytes(blobLocation, maxNBytes)
  }

  private def detectMimeType(
    filename: String,
    blob: BlobStorageRef
  )(implicit mat: Materializer): Future[String] = {
    import scala.compat.java8.FutureConverters.{toJava,toScala}
    toScala(Decider.mimeTypeDetector.detectMimeTypeAsync(filename, () => toJava(getBytes(blob.location))))
  }

  protected[ingest] def getContentTypeNoParameters(
    input: WrittenFile2
  )(implicit mat: Materializer): Future[String] = {
    val MediaTypeRegex = "^([^;]+).*$".r

    input.contentType match {
      case "application/octet-stream" => detectMimeType(input.filename, input.blob.ref)
      case MediaTypeRegex(withoutParameter) => Future.successful(withoutParameter)
      case _ => detectMimeType(input.filename, input.blob.ref)
    }
  }

  protected[ingest] def getNextStep(
    input: WrittenFile2
  )(implicit mat: Materializer): Future[Step] = {
    implicit val ec = mat.executionContext

    if (input.isCanceled) return Future.successful(NextStep.Canceled.forFile(input))

    for {
      detectedContentType <- getContentTypeNoParameters(input)
    } yield {
      val nextStep: NextStep = handlers.get(detectedContentType)
        .orElse(handlers.get(detectedContentType.replaceFirst("/.*", "/*")))
        .getOrElse(NextStep.Unhandled)
      nextStep.forFile(input)
    }
  }

  private def getOutletIndex(
    input: WrittenFile2
  )(implicit mat: Materializer): Future[Int] = {
    implicit val ec = mat.executionContext
    for {
      step <- getNextStep(input)
    } yield {
      logger.info("FileGroup {}: File {} '{}': ⇒ {}", input.fileGroupJob.fileGroupId, input.id, input.filename, step.id)
      steps.indexOf(step)
    }
  }
}

object Decider {
  protected[ingest] val mimeTypeDetector = new MimeTypeDetector
}
