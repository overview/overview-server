package org.overviewproject.jobhandler.filegroup.task.step

import java.io.InputStream

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.blocking
import scala.util.control.Exception.ultimately

import org.overviewproject.blobstorage.BlobBucketId
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.database.Slick.simple._
import org.overviewproject.database.SlickClient
import org.overviewproject.database.SlickSessionProvider
import org.overviewproject.jobhandler.filegroup.task.DocumentConverter
import org.overviewproject.jobhandler.filegroup.task.LibreOfficeDocumentConverter
import org.overviewproject.models.File
import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.models.TempDocumentSetFile
import org.overviewproject.models.tables.Files
import org.overviewproject.models.tables.GroupedFileUploads
import org.overviewproject.models.tables.TempDocumentSetFiles
import org.overviewproject.postgres.LargeObjectInputStream

trait CreateFileWithView extends TaskStep with LargeObjectMover with SlickClient {
  protected val documentSetId: Long
  protected val uploadedFileId: Long
  protected val converter: DocumentConverter

  protected val nextStep: File => TaskStep

  override def execute: Future[TaskStep] =
    for {
      upload <- findUpload
      contentsLocation <- moveLargeObjectToBlobStorage(upload.contentsOid, upload.size, BlobBucketId.FileContents)
      (viewLocation, viewSize) <- createView(upload)
      file = File(0l, 11, upload.name, contentsLocation, upload.size, viewLocation, viewSize)
      savedFile <- writeFile(file)
    } yield nextStep(savedFile)

  private def findUpload: Future[GroupedFileUpload] = db { implicit session =>
    GroupedFileUploads.filter(_.id === uploadedFileId).first
  }

  private def createView(upload: GroupedFileUpload): Future[(String, Long)] = blocking {
    withLargeObjectInputStream(upload.contentsOid) { stream =>
      converter.withStreamAsPdf(upload.guid, upload.name, stream) { (viewStream, viewSize) =>
        blobStorage.create(BlobBucketId.FileView, viewStream, viewSize)
          .map((_, viewSize))
      }
    }
  }

  private def writeFile(file: File): Future[File] = db { implicit session =>
    withTransaction(writeFileAndTempDocumentSetFile(file)(_))
  }

  private def writeFileAndTempDocumentSetFile(file: File)(implicit session: Session): File = {
    val fileId = (Files returning Files.map(_.id)) += file
    TempDocumentSetFiles += TempDocumentSetFile(documentSetId, fileId)
    file.copy(id = fileId)
  }

  private def withLargeObjectInputStream[T](oid: Long)(f: InputStream => T): T = {
    val stream = largeObjectInputStream(oid)

    ultimately(stream.close) {
      f(stream)
    }
  }
}

object CreateFileWithView {
  def apply(documentSetId: Long, uploadedFileId: Long, next: File => TaskStep): CreateFileWithView =
    new CreateFileWithViewImpl(documentSetId, uploadedFileId, next)

  private class CreateFileWithViewImpl(
    override protected val documentSetId: Long,
    override protected val uploadedFileId: Long,
    override protected val nextStep: File => TaskStep) extends CreateFileWithView with SlickSessionProvider {

    override protected val converter = LibreOfficeDocumentConverter
    override protected val blobStorage = BlobStorage
    
    override protected def largeObjectInputStream(oid: Long) = 
      new LargeObjectInputStream(oid, new SlickSessionProvider {})
  }

}