package com.overviewdocs.jobhandler.filegroup.task.step

import java.io.InputStream
import java.nio.file.{Files=>JFiles,Path}
import java.security.{DigestInputStream,MessageDigest}
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.blobstorage.{BlobBucketId,BlobStorage}
import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.jobhandler.filegroup.task.DocumentConverter
import com.overviewdocs.jobhandler.filegroup.task.LibreOfficeDocumentConverter
import com.overviewdocs.jobhandler.filegroup.task.TimeoutGenerator
import com.overviewdocs.models.{File,GroupedFileUpload,TempDocumentSetFile}
import com.overviewdocs.models.tables.{Files,TempDocumentSetFiles}
import com.overviewdocs.postgres.LargeObjectInputStream

/** Creates a view by converting the [[GroupedFileUpload] contents to PDF
  */
class CreateFileWithView(
  override protected val documentSetId: Long,
  protected val uploadedFile: GroupedFileUpload,
  val timeoutGenerator: TimeoutGenerator,
  val nextStep: File => TaskStep
)(override implicit protected val executor: ExecutionContext)
extends UploadedFileProcessStep with HasBlockingDatabase {
  import database.api._

  private val CopyBufferSize = 1024 * 1024 * 5 // Copy 5MB at a time from database
  override protected val filename = uploadedFile.name

  protected val converter: DocumentConverter = LibreOfficeDocumentConverter(timeoutGenerator)
  protected val blobStorage: BlobStorage = BlobStorage
  protected def largeObjectInputStream = new LargeObjectInputStream(uploadedFile.contentsOid, blockingDatabase)

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

  override protected def doExecute: Future[TaskStep] = {
    withTempFiles { case (rawPath, pdfPath) =>
      for {
        sha1 <- downloadLargeObjectAndCalculateSha1(rawPath)
        _ <- converter.convertFileToPdf(rawPath, pdfPath)
        pdfNBytes <- Future(blocking(JFiles.size(pdfPath)))
        contentsLocation <- blobStorage.create(BlobBucketId.FileContents, rawPath)
        viewLocation <- blobStorage.create(BlobBucketId.FileView, pdfPath)
        file <- createFile(uploadedFile.name, contentsLocation, uploadedFile.size, sha1, pdfNBytes, viewLocation)
      } yield nextStep(file)
    }
  }

  protected lazy val fileInserter = {
    Files
      .map(f => (f.referenceCount, f.name, f.contentsLocation, f.contentsSize, f.contentsSha1, f.viewLocation, f.viewSize))
      .returning(Files)
  }

  private def createFile(name: String,
                         contentsLocation: String, contentsSize: Long, sha1: Array[Byte],
                         viewSize: Long, viewLocation: String): Future[File] = {
    database.run((for {
      file <- fileInserter.+=(1, name, contentsLocation, contentsSize, sha1, viewLocation, viewSize)
      _ <- TempDocumentSetFiles.+=(TempDocumentSetFile(documentSetId, file.id))
    } yield file).transactionally)
  }

  private def downloadLargeObjectAndCalculateSha1(destination: Path): Future[Array[Byte]] = {
    Future(blocking(JFiles.newOutputStream(destination))).flatMap { outputStream =>
      val loStream = new LargeObjectInputStream(uploadedFile.contentsOid, blockingDatabase)
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
