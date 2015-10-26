package com.overviewdocs.jobhandler.filegroup.task.step

import java.nio.file.{Files=>JFiles,Path}
import java.security.{DigestInputStream,MessageDigest}
import java.util.Locale
import org.overviewproject.pdfocr.PdfOcr
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.blobstorage.{BlobBucketId,BlobStorage}
import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.models.{File,GroupedFileUpload,TempDocumentSetFile}
import com.overviewdocs.models.tables.{Files,TempDocumentSetFiles}
import com.overviewdocs.postgres.LargeObjectInputStream

/** Create a [[File]] from a PDF document.
  *
  * Does this:
  *
  * 1. Downloads the file from Postgres LargeObject and calculates its sha1.
  * 2. Makes a searchable copy, using PdfOcr.
  * 3. Uploads both copies to BlobStorage.
  * 4. Writes a File and a TempDocumentSetFile to Postgres.
  */
class CreatePdfFile(
  override protected val documentSetId: Long,
  override protected val filename: String,
  val upload: GroupedFileUpload,
  val language: Locale,
  val nextStep: File => TaskStep
)(override implicit protected val executor: ExecutionContext)
extends UploadedFileProcessStep with HasBlockingDatabase {
  import database.api._

  protected val CopyBufferSize = 1024 * 1024 * 5 // Copy 5MB at a time from database

  protected val blobStorage: BlobStorage = BlobStorage

  protected def downloadLargeObjectAndCalculateSha1(destination: Path): Future[Array[Byte]] = {
    Future(blocking(JFiles.newOutputStream(destination))).flatMap { outputStream =>
      val loStream = new LargeObjectInputStream(upload.contentsOid, blockingDatabase)
      val digest = MessageDigest.getInstance("SHA-1")
      val digestStream = new DigestInputStream(loStream, digest)

      val buf = new Array[Byte](CopyBufferSize)
      def step: Future[Unit] = {
        Future(blocking(loStream.read(buf))).flatMap { nBytes =>
          if (nBytes == -1) {
            Future.successful(())
          } else {
            Future(blocking(outputStream.write(buf))).flatMap(_ => step)
          }
        }
      }

      for {
        _ <- step
        _ <- Future(blocking(outputStream.close))
      } yield digest.digest
    }
  }

  private def dummyProgress(nPages: Int, nTotalPages: Int): Future[Unit] = Future.successful(())

  private def withTempFiles[A](f: (Path, Path) => Future[A]): Future[A] = {
    Future(blocking {
      (JFiles.createTempFile("create-pdf-file-raw", ".pdf"), JFiles.createTempFile("create-pdf-file-pdf", ".pdf"))
    }).flatMap { case (rawPath, pdfPath) =>
      def delete: Future[Unit] = {
        Future(blocking {
          JFiles.delete(rawPath)
          JFiles.delete(pdfPath)
          ()
        })(executor)
      }

      f(rawPath, pdfPath)
        .recoverWith[A] { case ex: Exception => delete.flatMap(_ => Future.failed[A](ex)) }
        .flatMap(a => delete.map(_ => a))
    }
  }

  override protected def doExecute: Future[TaskStep] = withTempFiles { (rawPath, pdfPath) =>
    for {
      sha1 <- downloadLargeObjectAndCalculateSha1(rawPath)
      _ <- PdfOcr.makeSearchablePdf(rawPath, pdfPath, Seq(language), dummyProgress)
      pdfNBytes <- Future(blocking(JFiles.size(pdfPath)))
      rawLocation <- BlobStorage.create(BlobBucketId.FileContents, rawPath)
      pdfLocation <- BlobStorage.create(BlobBucketId.FileContents, pdfPath)
      file <- writeDatabase(rawLocation, sha1, pdfLocation, pdfNBytes)
    } yield nextStep(file)
  }

  private lazy val fileInserter = {
    Files
      .map(f => (f.referenceCount, f.name, f.contentsLocation, f.contentsSize, f.contentsSha1, f.viewLocation, f.viewSize))
      .returning(Files)
  }

  private lazy val tempDocumentSetFileInserter = (TempDocumentSetFiles returning TempDocumentSetFiles)

  private def writeDatabase(rawLocation: String, sha1: Array[Byte], pdfLocation: String, pdfNBytes: Long): Future[File] = {
    database.run((for {
      file <- fileInserter.+=((1, upload.name, rawLocation, upload.size, sha1, pdfLocation, pdfNBytes))
      _ <- TempDocumentSetFiles.+=(TempDocumentSetFile(documentSetId, file.id))
    } yield file).transactionally)
  }
}

object CreatePdfFile {
  def apply(documentSetId: Long, filename: String, upload: GroupedFileUpload, lang: String, next: File => TaskStep)(implicit executor: ExecutionContext): CreatePdfFile = {
    new CreatePdfFile(documentSetId, filename, upload, new Locale(lang), next)
  }
}
