package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.database.Slick.simple._
import org.overviewproject.database.SlickClient
import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.models.{ File, TempDocumentSetFile }
import java.io.InputStream
import org.overviewproject.models.tables.{ Files, GroupedFileUploads, TempDocumentSetFiles }
import org.overviewproject.blobstorage.BlobBucketId

trait CreatePdfFile extends TaskStep with SlickClient {

  protected val documentSetId: Long
  protected val uploadedFileId: Long

  protected val blobStorage: BlobStorage
  protected def largeObjectInputStream(oid: Long): InputStream

  protected def nextStep(file: File): TaskStep

  override def execute: Future[TaskStep] = for {
    upload <- findUpload
    location <- moveLargeObjectToBlobStorage(upload.contentsOid, upload.size)
    file <- createFile(upload.name, upload.size, location)
  } yield nextStep(file)

  private def findUpload: Future[GroupedFileUpload] = db { implicit session =>
    GroupedFileUploads.filter(_.id === uploadedFileId).first
  }

  private def moveLargeObjectToBlobStorage(oid: Long, size: Long): Future[String] = {
    val loStream = largeObjectInputStream(oid)

    blobStorage.create(BlobBucketId.FileContents, loStream, size)
  }

  private def createFile(name: String, size: Long, location: String): Future[File] = db { implicit session =>
    val file = File(0l, 1, name, location, size, location, size)
    withTransaction(writeFileAndTempDocumentSetFile(file)(_))
  }

  private def writeFileAndTempDocumentSetFile(file: File)(implicit session: Session): File = {
    val fileId = (Files returning Files.map(_.id)) += file
    TempDocumentSetFiles += TempDocumentSetFile(documentSetId, fileId)
    file.copy(id = fileId)
  }
}