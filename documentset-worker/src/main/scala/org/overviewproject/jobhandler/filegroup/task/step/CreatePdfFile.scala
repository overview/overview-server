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
import org.overviewproject.models.{ File, GroupedFileUpload, TempDocumentSetFile }
import org.overviewproject.models.tables.{ Files, GroupedFileUploads, TempDocumentSetFiles }
import org.overviewproject.postgres.LargeObjectInputStream

trait CreatePdfFile extends TaskStep with LargeObjectMover with SlickClient {
  protected val documentSetId: Long
  protected val uploadedFile: GroupedFileUpload

  protected val nextStep: File => TaskStep

  override def execute: Future[TaskStep] = for {
    upload <- findUpload
    (location, sha1) <- moveLargeObjectToBlobStorage(upload.contentsOid, upload.size)
    file <- createFile(upload.name, upload.size, location, sha1)
  } yield nextStep(file)


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

  def apply(documentSetId: Long, uploadedFile: GroupedFileUpload, next: File => TaskStep): CreatePdfFile =
    new CreatePdfFileImpl(documentSetId, uploadedFile, next)

  private class CreatePdfFileImpl(
    override protected val documentSetId: Long,
    override protected val uploadedFile: GroupedFileUpload,
    override protected val nextStep: File => TaskStep) extends CreatePdfFile with SlickSessionProvider {

    override protected val blobStorage = BlobStorage
    override protected def largeObjectInputStream(oid: Long) =
      new LargeObjectInputStream(oid, new SlickSessionProvider {})

  }
}
