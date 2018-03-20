package com.overviewdocs.ingest.process.logic

import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO,Source,StreamConverters}
import akka.util.ByteString
import com.google.common.io.ByteStreams
import java.io.{IOException,InputStream}
import java.nio.file.{Files,Path}
import play.api.libs.json.{JsNumber,JsTrue}
import scala.concurrent.{Future,blocking}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.ingest.model.WrittenFile2
import com.overviewdocs.ingest.process.{StepLogic,StepOutputFragment}
import com.overviewdocs.models.File2
import com.overviewdocs.pdfocr.{PdfSplitter,SplitPdfAndExtractTextParser}
import com.overviewdocs.util.Logger

class SplitExtractStepLogic(
  inputStreamChunkSize: Int = 1024 * 1024 // 1MB
) extends StepLogic {
  private val logger = Logger.forClass(getClass)

  override def toChildFragments(
    blobStorage: BlobStorage,
    input: WrittenFile2
  )(implicit mat: Materializer): Source[StepOutputFragment, akka.NotUsed] = {
    implicit val ec = mat.executionContext

    // Two modes:
    // 1. Extract-only: executable will give us a list of pages, and we are to
    //    emit exactly one output file.
    // 2. Split-and-extract: executable will give us files and blobs, and we are
    //    to emit them as they come.
    val extractOnly = !input.pipelineOptions.splitByPage
    var allPagesText = ByteString.empty

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
      if (input.fileGroupJob.isCanceled) state.child.destroyForcibly

      state.parser.next
    }

    def tokenToFragments(token: SplitPdfAndExtractTextParser.Token): Vector[StepOutputFragment] = {
      import SplitPdfAndExtractTextParser.Token

      (input.fileGroupJob.isCanceled, token) match {
        case (true, _) => Vector(
          StepOutputFragment.Canceled
        )
        case (_, Token.Header(nPages)) => {
          nPagesTotal = nPages
          Vector()
        }
        case (_, Token.PageHeader(isOcr: Boolean)) => {
          pageNumber += 1
          val maybeHeader = if (!extractOnly || pageNumber == 1) {
            Vector(StepOutputFragment.File2Header(
              pageNumber - 1,
              input.filename,
              "application/pdf",
              input.languageCode,
              augmentedMetadata(isOcr, pageNumber),
              augmentedPipelineOptions
            ))
          } else {
            Vector()
          }
          val maybeInheritBlob = if (extractOnly && pageNumber == 1) {
            Vector(StepOutputFragment.InheritBlob)
          } else {
            Vector()
          }
          Vector(StepOutputFragment.ProgressChildren(pageNumber - 1, nPagesTotal)) ++ maybeHeader ++ maybeInheritBlob
        }
        case (_, Token.PageThumbnail(byteString)) => {
          if (byteString.nonEmpty) {
            Vector(StepOutputFragment.Thumbnail(pageNumber - 1, "image/png", Source.single(byteString)))
          } else {
            // we're extractOnly and not on the first page. No PDF.
            Vector()
          }
        }
        case (_, Token.PagePdf(byteString)) => {
          if (byteString.nonEmpty) {
            Vector(StepOutputFragment.Blob(pageNumber - 1, Source.single(byteString)))
          } else {
            // we're extractOnly and not on the first page. No PDF.
            Vector()
          }
        }
        case (_, Token.PageText(byteString)) => {
          if (extractOnly) {
            allPagesText = allPagesText ++ byteString
            if (pageNumber == nPagesTotal) {
              Vector(StepOutputFragment.Text(pageNumber - 1, Source.single(allPagesText)))
            } else {
              allPagesText = allPagesText ++ ByteString("\f")
              Vector()
            }
          } else {
            Vector(StepOutputFragment.Text(pageNumber - 1, Source.single(byteString)))
          }
        }
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
