package com.overviewdocs.jobhandler.filegroup.task

import java.io.IOException
import java.nio.file.{Files=>JFiles,Path}
import java.security.{DigestInputStream,MessageDigest}
import java.util.Locale
import org.overviewproject.pdfocr.PdfOcr
import org.overviewproject.pdfocr.exceptions._
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.blobstorage.{BlobBucketId,BlobStorage}
import com.overviewdocs.database.{HasBlockingDatabase,LargeObjectInputStream}
import com.overviewdocs.models.{File,GroupedFileUpload}
import com.overviewdocs.models.tables.Files

/** Creates a [[File]] from a PDF document.
  *
  * Does this:
  *
  * 1. Downloads the file from Postgres LargeObject and calculates its sha1.
  * 2. Makes a searchable copy, using PdfOcr (or if ocr == false, just copies).
  * 3. Uploads both copies to BlobStorage.
  * 4. Returns the File.
  *
  * If there's a recoverable error (i.e., the file is an invalid or
  * password-protected PDF), returns a String error message.
  *
  * TODO share some code with CreateOfficeFile.scala
  *
  * TODO delete the File if we cancel the job before creating Documents for it.
  * (We used to have a TempDocumentSetFile structure in the database, but that
  * was overkill and race-prond. We *ought* to be using the refcount field.)
  */
class CreatePdfFile(
  upload: GroupedFileUpload,
  ocr: Boolean,
  lang: String,
  onProgress: Double => Boolean
)(implicit ec: ExecutionContext) extends HasBlockingDatabase {
  import database.api._

  private case object PdfOcrCancelled extends Throwable

  private val CopyBufferSize = 1024 * 1024 * 5 // Copy 5MB at a time from database

  protected val blobStorage: BlobStorage = BlobStorage

  private def downloadLargeObjectAndCalculateSha1(destination: Path): Future[Array[Byte]] = {
    Future(blocking(JFiles.newOutputStream(destination))).flatMap { outputStream =>
      val loStream = new LargeObjectInputStream(upload.contentsOid, blockingDatabase)
      val digest = MessageDigest.getInstance("SHA-1")
      val digestStream = new DigestInputStream(loStream, digest)

      val buf = new Array[Byte](CopyBufferSize)
      def step: Future[Unit] = {
        Future(blocking(digestStream.read(buf))).flatMap { nBytes =>
          if (nBytes == -1) {
            Future.successful(())
          } else {
            Future(blocking(outputStream.write(buf, 0, nBytes))).flatMap(_ => step)
          }
        }
      }

      for {
        _ <- step
        _ <- Future(blocking(outputStream.close))
      } yield digest.digest
    }
  }

  private def progressCallback(nPages: Int, nTotalPages: Int): Boolean = {
    onProgress(nPages.toDouble / nTotalPages)
  }

  private def withTempFiles[A](f: (Path, Path) => Future[A]): Future[A] = {
    Future(blocking {
      (JFiles.createTempFile("create-pdf-file-raw", ".pdf"), JFiles.createTempFile("create-pdf-file-pdf", ".pdf"))
    }).flatMap { case (rawPath, pdfPath) =>
      def delete: Future[Unit] = {
        Future(blocking {
          //JFiles.delete(rawPath)
          //JFiles.delete(pdfPath)
          ()
        })
      }

      f(rawPath, pdfPath)
        .recoverWith[A] { case ex: Exception => delete.flatMap(_ => Future.failed[A](ex)) }
        .flatMap(a => delete.map(_ => a))
    }
  }

  def execute: Future[Either[String,File]] = {
    withTempFiles { (rawPath, pdfPath) =>
      for {
        sha1 <- downloadLargeObjectAndCalculateSha1(rawPath)
        pdfResult <- makeSearchablePdf(rawPath, pdfPath)
        result <- writeFileOnSuccess(pdfResult, rawPath, sha1, pdfPath)
      } yield result
    }
  }

  private def makeSearchablePdf(inPath: Path, outPath: Path): Future[Either[String,Unit]] = {
    if (ocr) {
      PdfNormalizer.makeSearchablePdf(inPath, outPath, lang, progressCallback)
    } else {
      Future(blocking(try {
        JFiles.copy(inPath, outPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        Right(())
      } catch {
        case e: IOException => Left(e.toString)
      }))
    }
  }

  def writeFileOnSuccess(
    result: Either[String,Unit],
    rawPath: Path,
    sha1: Array[Byte],
    pdfPath: Path
  ): Future[Either[String,File]] = {
    result match {
      case Left(message) => Future.successful(Left(message))
      case Right(()) => {
        for {
          pdfNBytes <- Future(blocking(JFiles.size(pdfPath)))
          rawLocation <- BlobStorage.create(BlobBucketId.FileContents, rawPath)
          pdfLocation <- BlobStorage.create(BlobBucketId.FileView, pdfPath)
          file <- writeDatabase(rawLocation, sha1, pdfLocation, pdfNBytes)
        } yield Right(file)
      }
    }
  }

  private lazy val fileInserter = {
    Files
      .map(f => (f.referenceCount, f.name, f.contentsLocation, f.contentsSize, f.contentsSha1, f.viewLocation, f.viewSize))
      .returning(Files)
  }

  private def writeDatabase(rawLocation: String, sha1: Array[Byte], pdfLocation: String, pdfNBytes: Long): Future[File] = {
    database.run(fileInserter.+=((1, upload.name, rawLocation, upload.size, sha1, pdfLocation, pdfNBytes)))
  }
}

object CreatePdfFile {
  def apply(
    upload: GroupedFileUpload,
    ocr: Boolean,
    lang: String,
    onProgress: Double => Boolean
  )(implicit ec: ExecutionContext): Future[Either[String,File]] = {
    new CreatePdfFile(upload, ocr, lang, onProgress).execute
  }
}
