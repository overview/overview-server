package com.overviewdocs.jobhandler.filegroup.task

import java.io.InputStream
import java.nio.file.{Files=>JFiles,Path}
import java.security.{DigestInputStream,MessageDigest}
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.blobstorage.{BlobBucketId,BlobStorage}
import com.overviewdocs.database.{HasBlockingDatabase,LargeObjectInputStream}
import com.overviewdocs.models.{File,GroupedFileUpload}
import com.overviewdocs.models.tables.Files

/** Creates a [[File]] from a LibreOffice-readable document.
  *
  * Does this:
  *
  * 1. Downloads the file from Postgres LargeObject and calculates its sha1.
  * 2. Makes a searchable copy, using LibreOffice to convert to PDF.
  * 3. Uploads both copies to BlobStorage.
  * 4. Returns the File.
  *
  * If there's a recoverabale error (e.g., LibreOffice crashes or times out),
  * returns a String error message.
  *
  * TODO share some code with CreatePdfFile.scala
  *
  * TODO delete the File if we cancel the job before creating Documents for it.
  * (We used to have a TempDocumentSetFile structure in the database, but that
  * was overkill and race-prond. We *ought* to be using the refcount field.)
  */
class CreateOfficeFile(upload: GroupedFileUpload)(implicit ec: ExecutionContext) extends HasBlockingDatabase {
  import database.api._

  private val CopyBufferSize = 1024 * 1024 * 5 // Copy 5MB at a time from database

  protected val converter: OfficeDocumentConverter = OfficeDocumentConverter
  protected val blobStorage: BlobStorage = BlobStorage

  protected def withTempFiles[A](f: (Path, Path) => Future[A]): Future[A] = {
    val paths: (Path, Path) = blocking {
      (
        JFiles.createTempFile("create-file-with-view", ".user-provided"),
        JFiles.createTempFile("create-file-with-view", ".pdf")
      )
    }

    f(paths._1, paths._2).andThen { case _ => blocking {
      JFiles.delete(paths._1)
      JFiles.delete(paths._2)
    }}
  }

  def execute: Future[Either[String,File]] = {
    withTempFiles { case (rawPath, pdfPath) =>
      for {
        sha1 <- downloadLargeObjectAndCalculateSha1(rawPath)
        tempPdfPath <- converter.convertFileToPdf(rawPath)
        _ <- Future(blocking(JFiles.move(tempPdfPath, pdfPath)))
        pdfNBytes <- Future(blocking(JFiles.size(pdfPath)))
        contentsLocation <- blobStorage.create(BlobBucketId.FileContents, rawPath)
        viewLocation <- blobStorage.create(BlobBucketId.FileView, pdfPath)
        file <- createFile(contentsLocation, sha1, pdfNBytes, viewLocation)
      } yield Right(file)
    }
      .recover {
        case OfficeDocumentConverter.LibreOfficeFailedException(message) => Left(message)
        case OfficeDocumentConverter.LibreOfficeTimedOutException() => Left("LibreOffice timed out")
      }
  }

  protected lazy val fileInserter = {
    Files
      .map(f => (f.referenceCount, f.name, f.contentsLocation, f.contentsSize, f.contentsSha1, f.viewLocation, f.viewSize))
      .returning(Files)
  }

  private def createFile(
    contentsLocation: String,
    sha1: Array[Byte],
    viewSize: Long,
    viewLocation: String
  ): Future[File] = {
    database.run(fileInserter.+=(1, upload.name, contentsLocation, upload.size, sha1, viewLocation, viewSize))
  }

  private def downloadLargeObjectAndCalculateSha1(destination: Path): Future[Array[Byte]] = {
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
}

object CreateOfficeFile {
  def apply(upload: GroupedFileUpload)(implicit ec: ExecutionContext): Future[Either[String,File]] = {
    new CreateOfficeFile(upload).execute
  }
}
