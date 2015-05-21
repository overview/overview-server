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

/**
 * Create a [[File]] with PDF content
 */
trait CreatePdfFile extends UploadedFileProcessStep with LargeObjectMover with SlickClient {
  protected val uploadedFile: GroupedFileUpload

  override protected val documentSetId: Long
  override protected val filename: String = uploadedFile.name
  
  protected val blobStorage: BlobStorage
  protected def largeObjectInputStream(oid: Long): InputStream

  protected val nextStep: File => TaskStep

  override protected def doExecute: Future[TaskStep] = for {
    upload <- findUpload
    (location, sha1) <- moveLargeObjectToBlobStorage(upload.contentsOid, upload.size)
    file <- createFile(upload.name, upload.size, location, sha1)
  } yield nextStep(file)


  private def findUpload: Future[GroupedFileUpload] = db { implicit session =>
    GroupedFileUploads.filter(_.id === uploadedFile.id).first
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
  
  protected lazy val insertInvoker = (Files.map(f => 
    (f.referenceCount, f.name, f.contentsLocation, f.contentsSize, f.contentsSha1, f.viewLocation, f.viewSize)) returning
      Files).insertInvoker 
      
  private def createFile(name: String, size: Long, location: String, sha1: Array[Byte]): Future[File] = db { implicit session =>
    
    withTransaction(writeFileAndTempDocumentSetFile(name, size, location, sha1)(_))
  }

  private def writeFileAndTempDocumentSetFile(name: String, size: Long, location: String, sha1: Array[Byte])(implicit session: Session): File = {
    val file =  insertInvoker.insert(1, name, location, size, Some(sha1), location, size)

    TempDocumentSetFiles += TempDocumentSetFile(documentSetId, file.id)
    file
  }
}

object CreatePdfFile {

  def apply(documentSetId: Long, filename: String, uploadedFile: GroupedFileUpload, next: File => TaskStep): CreatePdfFile =
    new CreatePdfFileImpl(documentSetId, filename, uploadedFile, next)

  private class CreatePdfFileImpl(
    override protected val documentSetId: Long,
    override protected val filename: String,
    override protected val uploadedFile: GroupedFileUpload,
    override protected val nextStep: File => TaskStep) extends CreatePdfFile with SlickSessionProvider {

    override protected val blobStorage = BlobStorage
    override protected def largeObjectInputStream(oid: Long) =
      new LargeObjectInputStream(oid, new SlickSessionProvider {})

  }
}
