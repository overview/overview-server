package com.overviewdocs.ingest.pipeline

import akka.stream.scaladsl.Source
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.ingest.convert.{MinimportBroker,MinimportWorkerType}
import com.overviewdocs.ingest.pipeline.step.MinimportStepLogic
import com.overviewdocs.models.File2

/** Chooses and executes a Pipeline based on the given file2's contentType.
  *
  * If `contentType` is `"application/octet-stream"`, autodetects it using
  * `filename` and the first few bytes of content.
  *
  * Writes "unhandled" to the File2 and returns it if no handler matches.
  */
class DetectingPipeline(
  handlers: Map[String, Pipeline],
  file2Writer: File2Writer
) extends Pipeline {
  override def process(file2: File2)(implicit ec: ExecutionContext): Source[File2, akka.NotUsed] = {
    val futureSource = detectContentType(file2).map(handlers.get _).map(_ match {
      case Some(pipeline) => pipeline.process(file2)
      case None => processUnhandled(file2)
    })

    Source.fromFutureSource(futureSource)
      .mapMaterializedValue(_ => akka.NotUsed)
  }

  private def detectContentType(file2: File2)(implicit ec: ExecutionContext): Future[String] = ???

  private def processUnhandled(file2: File2)(implicit ec: ExecutionContext): Source[File2, akka.NotUsed] = {
    val futureOutput = file2Writer.setProcessed(file2, 0, Some("unhandled"))
    Source.fromFuture(futureOutput)
  }
}

object DetectingPipeline {
  def apply(minimportBroker: MinimportBroker, file2Writer: File2Writer) = {
    def minimportStep(workerType: MinimportWorkerType): Step = new Step(
      new MinimportStepLogic(minimportBroker, workerType),
      file2Writer
    )

    object Steps {
      val PdfOcr = minimportStep(MinimportWorkerType.PdfOcr)
      val Office2Pdf = minimportStep(MinimportWorkerType.Office2Pdf)
      val Pdf2Pdf = minimportStep(MinimportWorkerType.Pdf2Pdf)
    }

    object Pipelines {
      val Pdf = new StepsPipeline(Vector(
        Steps.PdfOcr, // Run OCR
        Steps.Pdf2Pdf // Split pages and extract text
      ))

      val Office = new StepsPipeline(Vector(
        Steps.Office2Pdf, // Convert to PDF
        Steps.Pdf2Pdf          // Split pages and extract text
      ))

      // TODO:
      //val Zip = new StepsPipeline(Vector(
      //  Steps.Zip,
      //  Steps.Recurse
      //))
    }

    val handlers = Map(
      "application/pdf" -> Pipelines.Pdf,

      "application/clarisworks" -> Pipelines.Office,
      "application/excel" -> Pipelines.Office,
      "application/macwriteii" -> Pipelines.Office,
      "application/msexcel" -> Pipelines.Office,
      "application/mspowerpoint" -> Pipelines.Office,
      "application/msword" -> Pipelines.Office,
      "application/prs.plucker" -> Pipelines.Office,
      "application/rtf" -> Pipelines.Office,
      "application/tab-separated-values" -> Pipelines.Office,
      "application/vnd.corel-draw" -> Pipelines.Office,
      "application/vnd.lotus-1-2-3" -> Pipelines.Office,
      "application/vnd.lotus-wordpro" -> Pipelines.Office,
      "application/vnd.ms-excel" -> Pipelines.Office,
      "application/vnd.ms-excel.sheet.binary.macroenabled.12" -> Pipelines.Office,
      "application/vnd.ms-excel.sheet.macroenabled.12" -> Pipelines.Office,
      "application/vnd.ms-excel.template.macroenabled.12" -> Pipelines.Office,
      "application/vnd.ms-powerpoint" -> Pipelines.Office,
      "application/vnd.ms-powerpoint.presentation.macroenabled.12" -> Pipelines.Office,
      "application/vnd.ms-powerpoint.slideshow.macroEnabled.12" -> Pipelines.Office,
      "application/vnd.ms-powerpoint.template.macroenabled.12" -> Pipelines.Office,
      "application/vnd.ms-publisher" -> Pipelines.Office,
      "application/vnd.ms-word" -> Pipelines.Office,
      "application/vnd.ms-word.document.macroenabled.12" -> Pipelines.Office,
      "application/vnd.ms-word.template.macroenabled.12" -> Pipelines.Office,
      "application/vnd.ms-works" -> Pipelines.Office,
      "application/vnd.oasis.opendocument.chart" -> Pipelines.Office,
      "application/vnd.oasis.opendocument.chart-template" -> Pipelines.Office,
      "application/vnd.oasis.opendocument.graphics" -> Pipelines.Office,
      "application/vnd.oasis.opendocument.graphics-flat-xml" -> Pipelines.Office,
      "application/vnd.oasis.opendocument.graphics-template" -> Pipelines.Office,
      "application/vnd.oasis.opendocument.presentation" -> Pipelines.Office,
      "application/vnd.oasis.opendocument.presentation-flat-xml" -> Pipelines.Office,
      "application/vnd.oasis.opendocument.presentation-template" -> Pipelines.Office,
      "application/vnd.oasis.opendocument.spreadsheet" -> Pipelines.Office,
      "application/vnd.oasis.opendocument.spreadsheet-flat-xml" -> Pipelines.Office,
      "application/vnd.oasis.opendocument.spreadsheet-template" -> Pipelines.Office,
      "application/vnd.oasis.opendocument.text" -> Pipelines.Office,
      "application/vnd.oasis.opendocument.text-flat-xml" -> Pipelines.Office,
      "application/vnd.oasis.opendocument.text-master" -> Pipelines.Office,
      "application/vnd.oasis.opendocument.text-template" -> Pipelines.Office,
      "application/vnd.oasis.opendocument.text-web" -> Pipelines.Office,
      "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> Pipelines.Office,
      "application/vnd.openxmlformats-officedocument.presentationml.slide" -> Pipelines.Office,
      "application/vnd.openxmlformats-officedocument.presentationml.slideshow" -> Pipelines.Office,
      "application/vnd.openxmlformats-officedocument.presentationml.template" -> Pipelines.Office,
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> Pipelines.Office,
      "application/vnd.openxmlformats-officedocument.spreadsheetml.template" -> Pipelines.Office,
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> Pipelines.Office,
      "application/vnd.openxmlformats-officedocument.wordprocessingml.template" -> Pipelines.Office,
      "application/vnd.palm" -> Pipelines.Office,
      "application/vnd.stardivision.writer-global" -> Pipelines.Office,
      "application/vnd.sun.xml.calc" -> Pipelines.Office,
      "application/vnd.sun.xml.calc.template" -> Pipelines.Office,
      "application/vnd.sun.xml.draw" -> Pipelines.Office,
      "application/vnd.sun.xml.draw.template" -> Pipelines.Office,
      "application/vnd.sun.xml.impress" -> Pipelines.Office,
      "application/vnd.sun.xml.impress.template" -> Pipelines.Office,
      "application/vnd.sun.xml.writer" -> Pipelines.Office,
      "application/vnd.sun.xml.writer.global" -> Pipelines.Office,
      "application/vnd.sun.xml.writer.template" -> Pipelines.Office,
      "application/vnd.visio" -> Pipelines.Office,
      "application/vnd.wordperfect" -> Pipelines.Office,
      "application/wordperfect" -> Pipelines.Office,
      "application/x-123" -> Pipelines.Office,
      "application/x-aportisdoc" -> Pipelines.Office,
      "application/x-dbase" -> Pipelines.Office,
      "application/x-dbf" -> Pipelines.Office,
      "application/x-doc" -> Pipelines.Office,
      "application/x-dos_ms_excel" -> Pipelines.Office,
      "application/x-excel" -> Pipelines.Office,
      "application/x-extension-txt" -> Pipelines.Office,
      "application/x-fictionbook+xml" -> Pipelines.Office,
      "application/x-hwp" -> Pipelines.Office,
      "application/x-iwork-keynote-sffkey" -> Pipelines.Office,
      "application/x-msexcel" -> Pipelines.Office,
      "application/x-ms-excel" -> Pipelines.Office,
      "application/x-quattropro" -> Pipelines.Office,
      "application/x-t602" -> Pipelines.Office,
      "application/x-wpg" -> Pipelines.Office,
      "image/x-freehand" -> Pipelines.Office,

      // Text types: we're using Office now, but we probably shouldn't
      // https://www.pivotaltracker.com/story/show/76453196
      // https://www.pivotaltracker.com/story/show/76453264
      "application/csv" -> Pipelines.Office,
      "application/javascript" -> Pipelines.Office,
      "application/json" -> Pipelines.Office,
      "application/xml" -> Pipelines.Office,
      "text/comma-separated-values" -> Pipelines.Office,
      "text/html" -> Pipelines.Office, // LibreOffice is uniquely inept with HTML
      "text/*" -> Pipelines.Office
    )

    new DetectingPipeline(handlers, file2Writer)
  }
}
