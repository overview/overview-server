package com.overviewdocs.ingest.pipeline

import akka.stream.scaladsl.Source
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.ingest.File2Writer
import com.overviewdocs.ingest.convert.{MinimportBroker,MinimportWorkerType}
import com.overviewdocs.ingest.models.{WrittenFile2,ProcessedFile2}
import com.overviewdocs.ingest.pipeline.step.MinimportStepLogic

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
  override def process(file2: WrittenFile2)(implicit ec: ExecutionContext): Source[ProcessedFile2, akka.NotUsed] = {
    val futureSource = detectContentType(file2).map(handlers.get _).map(_ match {
      case Some(pipeline) => pipeline.process(file2)
      case None => processUnhandled(file2)
    })

    Source.fromFutureSource(futureSource)
      .mapMaterializedValue(_ => akka.NotUsed)
  }

  private def detectContentType(file2: WrittenFile2)(implicit ec: ExecutionContext): Future[String] = ???

  private def processUnhandled(file2: WrittenFile2)(implicit ec: ExecutionContext): Source[ProcessedFile2, akka.NotUsed] = {
    val futureOutput = file2Writer.setProcessed(file2, 0, Some("unhandled"))
    Source.fromFuture(futureOutput)
  }
}
