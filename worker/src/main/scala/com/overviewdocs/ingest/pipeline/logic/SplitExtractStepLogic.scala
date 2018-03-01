package com.overviewdocs.ingest.pipeline.logic

import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO,Source,StreamConverters}
import akka.util.ByteString
import com.google.common.io.ByteStreams
import java.io.{IOException,InputStream}
import java.nio.file.{Files,Path}
import play.api.libs.json.{JsNumber,JsTrue}
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.ingest.models.WrittenFile2
import com.overviewdocs.ingest.pipeline.{StepLogic,StepOutputFragment}
import com.overviewdocs.models.File2
import com.overviewdocs.pdfocr.{PdfSplitter,SplitPdfAndExtractTextParser}
import com.overviewdocs.util.Logger

class SplitExtractStepLogic(
  blobStorage: BlobStorage,
  inputStreamChunkSize: Int = 1024 * 1024 // 1MB
) extends StepLogic {
  private val logger = Logger.forClass(getClass)

  override def toChildFragments(
    input: WrittenFile2
  )(implicit ec: ExecutionContext, mat: Materializer): Source[StepOutputFragment, akka.NotUsed] = {
    case class State(
      child: Process,
      parser: SplitPdfAndExtractTextParser,
      tempFilePath: Path
    )
    var pageNumber: Int = 0   // icky hack
    var nPagesTotal: Int = 0 // icky hack

    val augmentedPipelineOptions: File2.PipelineOptions = input.pipelineOptions.copy(
      splitByPage=false,
      stepsRemaining=input.pipelineOptions.stepsRemaining.tail
    )

    def augmentedMetadata(isFromOcr: Boolean, pageNumber: Int): File2.Metadata = {
      var json = input.metadata.jsObject

      if (isFromOcr) {
        json.+=("isFromOcr" -> JsTrue)
      }

      if (input.pipelineOptions.splitByPage && nPagesTotal > 1) {
        json.+=("pageNumber" -> JsNumber(pageNumber))
      }

      File2.Metadata(json)
    }

    def streamToSource(inputStream: InputStream): Source[ByteString, _] = {
      StreamConverters.fromInputStream(() => inputStream, inputStreamChunkSize)
    }

    def open: Future[State] = {
      for {
        // 1. Download from BlobStorage to tempfile
        tempFilePath <- Future(blocking(Files.createTempFile("split-extract-" + input.blob.location, null)))
        _ <- blobStorage.get(input.blob.location).runWith(FileIO.toPath(tempFilePath))

        // 2. Launch pdfocr-native process and connect to its output
        process <- Future(blocking {
          val splitByPage = input.pipelineOptions.splitByPage
          val cmd = PdfSplitter.command(tempFilePath, splitByPage)
          logger.info(cmd.mkString(" "))

          val process = new ProcessBuilder(cmd: _*)
            .redirectError(ProcessBuilder.Redirect.INHERIT) // so Logger sees it
            .start
          process.getOutputStream.close
          process
        })
      } yield State(process, new SplitPdfAndExtractTextParser(process.getInputStream), tempFilePath)
    }

    def readToken(state: State): Future[Option[SplitPdfAndExtractTextParser.Token]] = {
      if (input.canceled.isCompleted) state.child.destroyForcibly

      state.parser.next
    }

    def tokenToFragments(token: SplitPdfAndExtractTextParser.Token): Vector[StepOutputFragment] = {
      import SplitPdfAndExtractTextParser.Token

      (input.canceled.isCompleted, token) match {
        case (true, _) => Vector(
          StepOutputFragment.Canceled
        )
        case (_, Token.Header(nPages)) => {
          nPagesTotal = nPages
          Vector()
        }
        case (_, Token.PageHeader(isOcr: Boolean)) => {
          pageNumber += 1
          Vector(
            StepOutputFragment.ProgressChildren(pageNumber - 1, nPagesTotal),
            StepOutputFragment.File2Header(
              input.filename,
              input.contentType,
              input.languageCode,
              augmentedMetadata(isOcr, pageNumber),
              augmentedPipelineOptions
            )
          )
        }
        case (_, Token.PageThumbnail(_, inputStream)) => Vector(
          StepOutputFragment.Thumbnail("image/png", streamToSource(inputStream))
        )
        case (_, Token.PagePdf(_, inputStream)) => Vector(
          StepOutputFragment.Blob(streamToSource(inputStream))
        )
        case (_, Token.PageText(_, inputStream)) => Vector(
          StepOutputFragment.Text(streamToSource(inputStream))
        )
        case (_, Token.PdfError(message)) => Vector(
          StepOutputFragment.FileError(message)
        )
        case (_, Token.InvalidInput(message)) => Vector(
          StepOutputFragment.StepError(new RuntimeException(message))
        )
        case (_, Token.Success) => Vector(
          StepOutputFragment.Done
        )
      }
    }

    def close(state: State): Future[akka.Done] = {
      Future(blocking {
        // 1. Delete tempfile
        Files.delete(state.tempFilePath)

        // 2. Destroy pdfocr-native with fire
        state.child.destroyForcibly // kill -9

        // Does child.waitFor block if the input stream buffer never
        // empties? Let's play it safe and empty the buffer.
        try {
          ByteStreams.exhaust(state.child.getInputStream)
        } catch {
          case _: java.io.IOException => {} // This error would not surprise us: the child is dead
        }

        state.child.waitFor // We ignore the process's retval.

        akka.Done
      })
    }

    Source.unfoldResourceAsync(open _, readToken _, close _)
      .mapConcat(tokenToFragments _)
  }
}
