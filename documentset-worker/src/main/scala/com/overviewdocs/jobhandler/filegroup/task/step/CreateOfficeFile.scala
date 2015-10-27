package com.overviewdocs.jobhandler.filegroup.task.step

import java.io.InputStream
import java.nio.file.{Files=>JFiles,Path}
import java.security.{DigestInputStream,MessageDigest}
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.blobstorage.{BlobBucketId,BlobStorage}
import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.jobhandler.filegroup.task.DocumentConverter
import com.overviewdocs.jobhandler.filegroup.task.FilePipelineParameters
import com.overviewdocs.jobhandler.filegroup.task.LibreOfficeDocumentConverter
import com.overviewdocs.models.{File,GroupedFileUpload,TempDocumentSetFile}
import com.overviewdocs.models.tables.{Files,TempDocumentSetFiles}
import com.overviewdocs.postgres.LargeObjectInputStream

/** Creates a [[File]] from a LibreOffice-readable document.
  *
  * Does this:
  *
  * 1. Downloads the file from Postgres LargeObject and calculates its sha1.
  * 2. Makes a searchable copy, using LibreOffice to convert to PDF.
  * 3. Uploads both copies to BlobStorage.
  * 4. Writes a File and a TempDocumentSetFile to Postgres.
  * 5. Returns the File.
  *
  * If there's a recoverabale error (e.g., LibreOffice crashes or times out),
  * returns a String error message.
  *
  * TODO share some code with CreatePdfFile.scala
  */
class CreateOfficeFile(params: FilePipelineParameters)(implicit ec: ExecutionContext) extends HasBlockingDatabase {
  import database.api._

  private val CopyBufferSize = 1024 * 1024 * 5 // Copy 5MB at a time from database

  protected val converter: DocumentConverter = LibreOfficeDocumentConverter(params.timeoutGenerator)
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
        _ <- converter.convertFileToPdf(rawPath, pdfPath)
        pdfNBytes <- Future(blocking(JFiles.size(pdfPath)))
        contentsLocation <- blobStorage.create(BlobBucketId.FileContents, rawPath)
        viewLocation <- blobStorage.create(BlobBucketId.FileView, pdfPath)
        file <- createFile(contentsLocation, sha1, pdfNBytes, viewLocation)
      } yield Right(file)
    }
  }

  protected lazy val fileInserter = {
    Files
      .map(f => (f.referenceCount, f.name, f.contentsLocation, f.contentsSize, f.contentsSha1, f.viewLocation, f.viewSize))
      .returning(Files)
  }

  private def createFile(contentsLocation: String, sha1: Array[Byte],
                         viewSize: Long, viewLocation: String): Future[File] = {
    database.run((for {
      file <- fileInserter.+=(1, params.filename, contentsLocation, params.inputSize, sha1, viewLocation, viewSize)
      _ <- TempDocumentSetFiles.+=(TempDocumentSetFile(params.documentSetId, file.id))
    } yield file).transactionally)
  }

  private def downloadLargeObjectAndCalculateSha1(destination: Path): Future[Array[Byte]] = {
    Future(blocking(JFiles.newOutputStream(destination))).flatMap { outputStream =>
      val loStream = new LargeObjectInputStream(params.inputOid, blockingDatabase)
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
  def apply(params: FilePipelineParameters)(implicit ec: ExecutionContext): Future[Either[String,File]] = {
    new CreateOfficeFile(params).execute
  }
}
