package org.overviewproject.jobhandler.filegroup.task.step

import java.io.InputStream

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.blocking
import scala.util.control.Exception.ultimately

import org.overviewproject.blobstorage.BlobBucketId
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.database.HasBlockingDatabase
import org.overviewproject.jobhandler.filegroup.task.DocumentConverter
import org.overviewproject.jobhandler.filegroup.task.LibreOfficeDocumentConverter
import org.overviewproject.models.File
import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.models.TempDocumentSetFile
import org.overviewproject.models.tables.Files
import org.overviewproject.models.tables.GroupedFileUploads
import org.overviewproject.models.tables.TempDocumentSetFiles
import org.overviewproject.postgres.LargeObjectInputStream

/**
 * Creates a view by converting the [[GroupedFileUpload] contents to PDF
 */
trait CreateFileWithView extends UploadedFileProcessStep with LargeObjectMover with HasBlockingDatabase {
  import database.api._

  protected val documentSetId: Long
  protected val uploadedFile: GroupedFileUpload
  protected val converter: DocumentConverter

  override protected lazy val filename = uploadedFile.name

  protected val nextStep: File => TaskStep

  override protected def doExecute: Future[TaskStep] =
    for {
      (contentsLocation, sha1) <- moveLargeObjectToBlobStorage(uploadedFile.contentsOid, uploadedFile.size, BlobBucketId.FileContents)
      (viewLocation, viewSize) <- createView(uploadedFile)
      file <- createFile(uploadedFile.name, uploadedFile.size, contentsLocation, sha1, viewSize, viewLocation)
    } yield nextStep(file)

  private def createView(upload: GroupedFileUpload): Future[(String, Long)] = blocking {
    withLargeObjectInputStream(uploadedFile.contentsOid) { stream =>
      converter.withStreamAsPdf(uploadedFile.guid, stream) { (viewStream, viewSize) =>
        blobStorage.create(BlobBucketId.FileView, viewStream, viewSize)
          .map((_, viewSize))
      }
    }
  }

  protected lazy val fileInserter = (
    Files.map(f => (
      f.referenceCount, f.name, f.contentsLocation, f.contentsSize, f.contentsSha1, f.viewLocation, f.viewSize
    )) returning Files
  )

  private def createFile(name: String,
                         contentsSize: Long, contentsLocation: String, sha1: Array[Byte],
                         viewSize: Long, viewLocation: String): Future[File] = {
    database.run((for {
      file <- fileInserter.+=(1, name, contentsLocation, contentsSize, sha1, viewLocation, viewSize)
      _ <- TempDocumentSetFiles.+=(TempDocumentSetFile(documentSetId, file.id))
    } yield file).transactionally)
  }

  private def withLargeObjectInputStream[T](oid: Long)(f: InputStream => T): T = {
    val stream = largeObjectInputStream(oid)

    ultimately(stream.close) {
      f(stream)
    }
  }
}

object CreateFileWithView {
  def apply(documentSetId: Long, uploadedFile: GroupedFileUpload, next: File => TaskStep)
    (implicit executor: ExecutionContext): CreateFileWithView =
    new CreateFileWithViewImpl(documentSetId, uploadedFile, next)

  private class CreateFileWithViewImpl(
    override protected val documentSetId: Long,
    override protected val uploadedFile: GroupedFileUpload,
    override protected val nextStep: File => TaskStep
  )(override protected implicit val executor: ExecutionContext) extends CreateFileWithView {
    override protected val converter = LibreOfficeDocumentConverter
    override protected val blobStorage = BlobStorage

    override protected def largeObjectInputStream(oid: Long) = new LargeObjectInputStream(oid, blockingDatabase)
  }
}
