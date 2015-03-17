package org.overviewproject.jobhandler.filegroup.task.step

import java.io.{BufferedInputStream,InputStream}
import java.security.{DigestInputStream,MessageDigest}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.overviewproject.blobstorage.BlobBucketId
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.database.SlickClient
import org.overviewproject.database.SlickSessionProvider
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.{ File, TempDocumentSetFile }
import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.models.tables.{ Files, GroupedFileUploads, TempDocumentSetFiles }
import org.overviewproject.postgres.LargeObjectInputStream

trait CreatePdfFile extends TaskStep with SlickClient {
  protected val documentSetId: Long
  protected val uploadedFileId: Long

  protected val blobStorage: BlobStorage
  protected def largeObjectInputStream(oid: Long): InputStream

  protected def nextStep(file: File): TaskStep

  override def execute: Future[TaskStep] = for {
    upload <- findUpload
    (location, sha1) <- moveLargeObjectToBlobStorage(upload.contentsOid, upload.size)
    file <- createFile(upload.name, upload.size, location, sha1)
  } yield nextStep(file)

  private def findUpload: Future[GroupedFileUpload] = db { implicit session =>
    GroupedFileUploads.filter(_.id === uploadedFileId).first
  }

  /** Returns (blobLocation,sha1). */
  private def moveLargeObjectToBlobStorage(oid: Long, size: Long): Future[(String,Array[Byte])] = {
    val loStream = largeObjectInputStream(oid)
    val digest = MessageDigest.getInstance("SHA-1")
    val digestStream = new DigestInputStream(loStream, digest)

    for {
      location <- blobStorage.create(BlobBucketId.FileContents, digestStream, size)
    } yield (location, digest.digest)
  }

  private def createFile(name: String, size: Long, location: String, sha1: Array[Byte]): Future[File] = db { implicit session =>
    val file = File(0l, 1, name, location, size, Some(sha1), location, size)
    withTransaction(writeFileAndTempDocumentSetFile(file)(_))
  }

  private def writeFileAndTempDocumentSetFile(file: File)(implicit session: Session): File = {
    val fileId = (Files returning Files.map(_.id)) += file
    TempDocumentSetFiles += TempDocumentSetFile(documentSetId, fileId)
    file.copy(id = fileId)
  }
}

object CreatePdfFile {
  private val LargeObjectBufferSize = 5 * 1024 * 1024
  
  def apply(documentSetId: Long, uploadedFileId: Long): CreatePdfFile = 
    new CreatePdfFileImpl(documentSetId, uploadedFileId)
  
  private class CreatePdfFileImpl(
    override protected val documentSetId: Long,
    override protected val uploadedFileId: Long
  ) extends CreatePdfFile with SlickSessionProvider {

    override protected val blobStorage = BlobStorage
    override protected def largeObjectInputStream(oid: Long) = {
      val is = new LargeObjectInputStream(oid, new SlickSessionProvider {})
      new BufferedInputStream(is, LargeObjectBufferSize)
    }
    
    override protected def nextStep(file: File) = ExtractTextFromPdf(documentSetId, file, null)
  }
}
