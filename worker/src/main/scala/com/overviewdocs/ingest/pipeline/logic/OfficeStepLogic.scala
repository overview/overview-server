package com.overviewdocs.ingest.pipeline.logic

import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO,Source}
import akka.util.ByteString
import java.nio.file.{Files,Path}
import scala.concurrent.{ExecutionContext,Future,blocking}
import scala.util.{Success,Failure}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.jobhandler.filegroup.task.OfficeDocumentConverter
import com.overviewdocs.jobhandler.filegroup.task.OfficeDocumentConverter.LibreOfficeFailedException
import com.overviewdocs.jobhandler.filegroup.task.OfficeDocumentConverter.LibreOfficeTimedOutException
import com.overviewdocs.ingest.pipeline.{StepLogic,StepOutputFragment}
import com.overviewdocs.ingest.models.WrittenFile2

/** Converts Office documents to PDF.
  *
  * TODO handle cancelation. Progress reporting would be very hard and not
  * very useful. Cancellation would be easier and very useful.
  *
  * This converter uses a tempfile for input and one for output. The lifecycle
  * goes like this:
  *
  * 1. Download blob to inputTempfile
  * 2. Convert to PDF to create outputTempfile, and build Source from that
  * 3. Delete inputTempfile
  * 4. Return stream
  * 5. On Source complete, delete outputTempfile
  */
class OfficeStepLogic extends StepLogic {
  override def toChildFragments(
    blobStorage: BlobStorage,
    input: WrittenFile2
  )(implicit ec: ExecutionContext, mat: Materializer): Source[StepOutputFragment, akka.NotUsed] = {
    // File extension, if the input has one
    val ext: String = input.filename.replaceFirst(".*\\.", "") match {
      case s if s.matches("[a-zA-Z0-9]{1,5}") && s != input.filename => "." + s
      case _ => ".tmp"
    }

    def downloadTempfile: Future[Path] = {
      for {
        tempFilePath <- Future(blocking(Files.createTempFile("office-" + input.blob.location, ext)))
        _ <- blobStorage.get(input.blob.location).runWith(FileIO.toPath(tempFilePath))
      } yield tempFilePath
    }

    def tempfileToSource(inPath: Path): Future[Source[StepOutputFragment, akka.NotUsed]] = {
      OfficeDocumentConverter.convertFileToPdf(inPath).transform(_ match {
        case Success(outPath) => Success(outfileToSource(outPath))
        case Failure(ex: LibreOfficeTimedOutException) => Success(errorToSource(ex.getMessage))
        case Failure(ex: LibreOfficeFailedException) => Success(errorToSource(ex.getMessage))
        case Failure(ex: Throwable) => Failure(ex)
      })
    }

    def outfileToSource(outPath: Path): Source[StepOutputFragment, akka.NotUsed] = {
      // blobSource: deletes file immediately after stream is finished
      //
      // Nobody will be listening for the final materialized value.
      val blobSource: Source[ByteString, akka.NotUsed] = FileIO.fromPath(outPath)
        .watchTermination() { (result, futureDone) =>
          futureDone.onComplete { case _ => Future(blocking(Files.delete(outPath))) }
          akka.NotUsed
        }

      Source(Vector(
        StepOutputFragment.File2Header(
          input.filename,
          "application/pdf",
          input.languageCode,
          input.metadata,
          input.pipelineOptions.copy(ocr=false, stepsRemaining=input.pipelineOptions.stepsRemaining.tail)
        ),
        StepOutputFragment.Blob(FileIO.fromPath(outPath)),
        StepOutputFragment.Done
      ))
    }

    def errorToSource(message: String): Source[StepOutputFragment, akka.NotUsed] = {
      Source.single(StepOutputFragment.FileError(message))
    }

    val futureSource = for {
      inPath <- downloadTempfile
      source <- tempfileToSource(inPath)
      _ <- Future(blocking { Files.delete(inPath) })
    } yield {
      source
    }

    Source.fromFutureSource(futureSource)
      .mapMaterializedValue(_ => akka.NotUsed)
  }
}
