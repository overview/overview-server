package com.overviewdocs.ingest.pipeline.logic

import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO,Framing,Source,StreamConverters}
import akka.util.ByteString
import com.google.common.io.ByteStreams
import java.io.{IOException,InputStream}
import java.nio.file.{Files,Path}
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.ingest.models.WrittenFile2
import com.overviewdocs.ingest.pipeline.{StepLogic,StepOutputFragment}
import com.overviewdocs.models.File2
import com.overviewdocs.pdfocr.PdfNormalizer
import com.overviewdocs.util.Logger

/** Makes a PDF into a searchable PDF using pdfocr.
  *
  * The actual conversion happens in a separate process, because some user input
  * causes OutOfMemoryError.
  *
  * Does this:
  *
  * 1. Downloads inputTempfile from BlobStorage
  * 2. Creates outputTempfile to write to
  * 3. Starts MakeSearchablePdf (a separate process) and connects to its output
  * 4. Generates Progress fragments from stdout messages. Kills child if
  *    input.canceled.isCompleted.
  * 5. Generates an Error fragment on error or File2Header, Blob and Done if
  *    the output stream concluded.
  * 6. When completed, deletes temporary files.
  */
class OcrStepLogic(
  inputStreamChunkSize: Int = 1024 * 1024 // 1MB
) extends StepLogic {
  private val ProgressRegex = """^(\d+)/(\d+)$""".r
  private val logger = Logger.forClass(getClass)

  override def toChildFragments(
    blobStorage: BlobStorage,
    input: WrittenFile2
  )(implicit ec: ExecutionContext, mat: Materializer): Source[StepOutputFragment, akka.NotUsed] = {
    val futureSource = for {
      // 1. Download from BlobStorage to tempfile
      inPath <- Future(blocking(Files.createTempFile("split-extract-" + input.blob.location, ".pdf")))
      _ <- blobStorage.get(input.blob.location).runWith(FileIO.toPath(inPath))
      // 2. Create empty outPath
      outPath <- Future(blocking(Files.createTempFile("split-extract-out-" + input.blob.location, ".pdf")))
      // 3. Build Process
      process <- Future(blocking {
        val cmd = PdfNormalizer.command(inPath, outPath, input.languageCode)
        logger.info(cmd.mkString(" "))

        val process: Process = new ProcessBuilder(cmd: _*)
          .redirectError(ProcessBuilder.Redirect.INHERIT) // Output errors where Logger can see them
          .start
        process.getOutputStream.close
        process
      })
    } yield {
      StreamConverters.fromInputStream(process.getInputStream _)
        .via(Framing.delimiter(ByteString("\n"), maximumFrameLength=10240, allowTruncation=true))
        .concat(Source.single(ByteString("END"))) // so we can produce fragments after the end of the stream
        .mapConcat { lineUtf8 =>
          if (input.fileGroupJob.isCanceled) {
            process.destroyForcibly
            Vector(StepOutputFragment.Canceled) // we may generate many of these
          } else {
            lineUtf8.utf8String match {
              case ProgressRegex(num, den) => Vector(StepOutputFragment.ProgressChildren(num.toInt, den.toInt))
              case "END" => {
                val blob = FileIO.fromPath(outPath)
                  .mapMaterializedValue(_ => akka.NotUsed) // ignore I/O oddities

                Vector(
                  StepOutputFragment.File2Header(
                    input.filename,
                    input.contentType,
                    input.languageCode,
                    input.metadata,
                    input.pipelineOptions.copy(ocr=false, stepsRemaining=input.pipelineOptions.stepsRemaining.tail)
                  ),
                  StepOutputFragment.Blob(blob),
                  StepOutputFragment.Done
                )
              }
              case err: String if err.nonEmpty => Vector(StepOutputFragment.FileError(err))
              case _ => Vector()
            }
          }
        }
        .watchTermination()((notUsed, futureDone) => futureDone.onComplete { _ =>
          Future(blocking {
            process.destroyForcibly
            process.waitFor
            Files.delete(inPath)
            Files.delete(outPath)
          })
          akka.NotUsed
        })
    }

    Source.fromFutureSource(futureSource)
      .mapMaterializedValue(_ => akka.NotUsed)
  }
}
