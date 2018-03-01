package com.overviewdocs.ingest

import akka.stream.{Graph,UniformFanOutShape}
import scala.concurrent.ExecutionContext
import com.overviewdocs.ingest.models.WrittenFile2

object Decider {
  /** Augments WrittenFile2, adding pipelineOptions.remainingSteps if it isn't
    * set and creating a PendingProcessTask.
    */
  def decide(
    file2Writer: File2Writer,
    parallelism: Int = 1
  )(implicit ec: ExecutionContext): Graph[UniformFanOutShape[WrittenFile2, WrittenFile2], akka.NotUsed] = ???

//  private def minimportStep(stepId: StepId, workerType: MinimportWorkerType): Step = new Step(
//    stepId,
//    new MinimportStepLogic(minimportBroker, workerType),
//    file2Writer
//  )
//
//  object steps {
//    val PdfOcr = minimportStep(StepId.PdfOcr, MinimportWorkerType.PdfOcr)
//    val Office2Pdf = minimportStep(StepId.Office, MinimportWorkerType.Office2Pdf)
//    val Pdf2Pdf = minimportStep(StepId.PdfSplitExtract, MinimportWorkerType.Pdf2Pdf)
//  }
//
//  object pipelines {
//    val Pdf = Vector(
//      steps.PdfOcr, // Run OCR
//      steps.Pdf2Pdf // Split pages and extract text
//    )
//
//    val Office = Vector(
//      steps.Office2Pdf, // Convert to PDF
//      steps.Pdf2Pdf     // Split pages and extract text
//    )
//
//    // TODO:
//    //val Zip = Vector(
//    //  steps.Zip,
//    //  steps.Recurse
//    //)
//  }
//
//  val handlers = Map(
//    "application/pdf" -> pipelines.Pdf,
//
//    "application/clarisworks" -> pipelines.Office,
//    "application/excel" -> pipelines.Office,
//    "application/macwriteii" -> pipelines.Office,
//    "application/msexcel" -> pipelines.Office,
//    "application/mspowerpoint" -> pipelines.Office,
//    "application/msword" -> pipelines.Office,
//    "application/prs.plucker" -> pipelines.Office,
//    "application/rtf" -> pipelines.Office,
//    "application/tab-separated-values" -> pipelines.Office,
//    "application/vnd.corel-draw" -> pipelines.Office,
//    "application/vnd.lotus-1-2-3" -> pipelines.Office,
//    "application/vnd.lotus-wordpro" -> pipelines.Office,
//    "application/vnd.ms-excel" -> pipelines.Office,
//    "application/vnd.ms-excel.sheet.binary.macroenabled.12" -> pipelines.Office,
//    "application/vnd.ms-excel.sheet.macroenabled.12" -> pipelines.Office,
//    "application/vnd.ms-excel.template.macroenabled.12" -> pipelines.Office,
//    "application/vnd.ms-powerpoint" -> pipelines.Office,
//    "application/vnd.ms-powerpoint.presentation.macroenabled.12" -> pipelines.Office,
//    "application/vnd.ms-powerpoint.slideshow.macroEnabled.12" -> pipelines.Office,
//    "application/vnd.ms-powerpoint.template.macroenabled.12" -> pipelines.Office,
//    "application/vnd.ms-publisher" -> pipelines.Office,
//    "application/vnd.ms-word" -> pipelines.Office,
//    "application/vnd.ms-word.document.macroenabled.12" -> pipelines.Office,
//    "application/vnd.ms-word.template.macroenabled.12" -> pipelines.Office,
//    "application/vnd.ms-works" -> pipelines.Office,
//    "application/vnd.oasis.opendocument.chart" -> pipelines.Office,
//    "application/vnd.oasis.opendocument.chart-template" -> pipelines.Office,
//    "application/vnd.oasis.opendocument.graphics" -> pipelines.Office,
//    "application/vnd.oasis.opendocument.graphics-flat-xml" -> pipelines.Office,
//    "application/vnd.oasis.opendocument.graphics-template" -> pipelines.Office,
//    "application/vnd.oasis.opendocument.presentation" -> pipelines.Office,
//    "application/vnd.oasis.opendocument.presentation-flat-xml" -> pipelines.Office,
//    "application/vnd.oasis.opendocument.presentation-template" -> pipelines.Office,
//    "application/vnd.oasis.opendocument.spreadsheet" -> pipelines.Office,
//    "application/vnd.oasis.opendocument.spreadsheet-flat-xml" -> pipelines.Office,
//    "application/vnd.oasis.opendocument.spreadsheet-template" -> pipelines.Office,
//    "application/vnd.oasis.opendocument.text" -> pipelines.Office,
//    "application/vnd.oasis.opendocument.text-flat-xml" -> pipelines.Office,
//    "application/vnd.oasis.opendocument.text-master" -> pipelines.Office,
//    "application/vnd.oasis.opendocument.text-template" -> pipelines.Office,
//    "application/vnd.oasis.opendocument.text-web" -> pipelines.Office,
//    "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> pipelines.Office,
//    "application/vnd.openxmlformats-officedocument.presentationml.slide" -> pipelines.Office,
//    "application/vnd.openxmlformats-officedocument.presentationml.slideshow" -> pipelines.Office,
//    "application/vnd.openxmlformats-officedocument.presentationml.template" -> pipelines.Office,
//    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> pipelines.Office,
//    "application/vnd.openxmlformats-officedocument.spreadsheetml.template" -> pipelines.Office,
//    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> pipelines.Office,
//    "application/vnd.openxmlformats-officedocument.wordprocessingml.template" -> pipelines.Office,
//    "application/vnd.palm" -> pipelines.Office,
//    "application/vnd.stardivision.writer-global" -> pipelines.Office,
//    "application/vnd.sun.xml.calc" -> pipelines.Office,
//    "application/vnd.sun.xml.calc.template" -> pipelines.Office,
//    "application/vnd.sun.xml.draw" -> pipelines.Office,
//    "application/vnd.sun.xml.draw.template" -> pipelines.Office,
//    "application/vnd.sun.xml.impress" -> pipelines.Office,
//    "application/vnd.sun.xml.impress.template" -> pipelines.Office,
//    "application/vnd.sun.xml.writer" -> pipelines.Office,
//    "application/vnd.sun.xml.writer.global" -> pipelines.Office,
//    "application/vnd.sun.xml.writer.template" -> pipelines.Office,
//    "application/vnd.visio" -> pipelines.Office,
//    "application/vnd.wordperfect" -> pipelines.Office,
//    "application/wordperfect" -> pipelines.Office,
//    "application/x-123" -> pipelines.Office,
//    "application/x-aportisdoc" -> pipelines.Office,
//    "application/x-dbase" -> pipelines.Office,
//    "application/x-dbf" -> pipelines.Office,
//    "application/x-doc" -> pipelines.Office,
//    "application/x-dos_ms_excel" -> pipelines.Office,
//    "application/x-excel" -> pipelines.Office,
//    "application/x-extension-txt" -> pipelines.Office,
//    "application/x-fictionbook+xml" -> pipelines.Office,
//    "application/x-hwp" -> pipelines.Office,
//    "application/x-iwork-keynote-sffkey" -> pipelines.Office,
//    "application/x-msexcel" -> pipelines.Office,
//    "application/x-ms-excel" -> pipelines.Office,
//    "application/x-quattropro" -> pipelines.Office,
//    "application/x-t602" -> pipelines.Office,
//    "application/x-wpg" -> pipelines.Office,
//    "image/x-freehand" -> pipelines.Office,
//
//    // Text types: we're using Office now, but we probably shouldn't
//    // https://www.pivotaltracker.com/story/show/76453196
//    // https://www.pivotaltracker.com/story/show/76453264
//    "application/csv" -> pipelines.Office,
//    "application/javascript" -> pipelines.Office,
//    "application/json" -> pipelines.Office,
//    "application/xml" -> pipelines.Office,
//    "text/comma-separated-values" -> pipelines.Office,
//    "text/html" -> pipelines.Office, // TODO anything else: LibreOffice is uniquely inept with HTML
//    "text/*" -> pipelines.Office
//  )
}
