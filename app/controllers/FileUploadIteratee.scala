package controllers

import java.util.UUID

import org.postgresql.PGConnection
import org.squeryl.PrimitiveTypeMode.using
import org.squeryl.Session

import com.jolbox.bonecp.ConnectionHandle

import models.orm.SquerylPostgreSqlAdapter
import models.upload.LO
import models.upload.OverviewUpload
import play.api.db.DB
import play.api.libs.iteratee.Iteratee
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.mvc.Results.InternalServerError
import play.api.Play.current

/**
 * Manages the upload of a file. Responsible for making sure the OverviewUpload object
 * is in sync with the LargeObject where the file is stored.
 */
trait FileUploadIteratee {
  
  /** package for information extracted from request header */
  private case class UploadInfo(filename: String, start: Long, contentLength: Long)

  /** extract useful information from request header */
  private object UploadInfo {
    def apply(header: RequestHeader): Option[UploadInfo] = {
      for {
        contentDisposition <- header.headers.get("CONTENT-DISPOSITION") 
        contentLength <- header.headers.get("CONTENT-LENGTH")
      } yield {
        val disposition = "[^=]*=\"?([^\"]*)\"?".r // attachment ; filename="foo.bar" (optional quotes) TODO: Handle quoted quotes
        val disposition(filename) = contentDisposition
        UploadInfo(filename, 0, contentLength.toLong)
      }
    }
  }
  
  /**
   * Checks the validity of the requests and processes the upload.
   */
  def store(userId: Long, guid: UUID, requestHeader: RequestHeader): Iteratee[Array[Byte], Either[Result, OverviewUpload]] = {

    val upload = UploadInfo(requestHeader).flatMap { r =>
      findUpload(userId, guid).map { u =>
      	if (r.start == 0) u.truncate
      	else u
      }.orElse(createUpload(userId, guid, r.filename, r.contentLength))
    }
    
    Iteratee.fold[Array[Byte], Option[OverviewUpload]](upload) { (upload, chunk) =>
      upload.flatMap(appendChunk(_, chunk))
    } mapDone {
      case Some(upload) => Right(upload)
      case None => Left(InternalServerError)  // Result of error when accessing database
    }
  }

  // Find an existing upload attempt
  def findUpload(userId: Long, guid: UUID): Option[OverviewUpload]

  // create a new upload attempt
  def createUpload(userId: Long, guid: UUID, filename: String, contentLength: Long): Option[OverviewUpload]

  // process a chunk of file data. @return the current OverviewUpload status, or None on failure	  
  def appendChunk(upload: OverviewUpload, chunk: Array[Byte]): Option[OverviewUpload]
}


/** Implementation that writes to database */
object FileUploadIteratee extends FileUploadIteratee {

  def findUpload(userId: Long, guid: UUID) = withPgConnection { implicit c => OverviewUpload.find(userId, guid) }

  def createUpload(userId: Long, guid: UUID, filename: String, contentLength: Long): Option[OverviewUpload] = withPgConnection { implicit c =>
    LO.withLargeObject { lo => OverviewUpload(userId, guid, filename, contentLength, lo.oid).save }
  }

  def appendChunk(upload: OverviewUpload, chunk: Array[Byte]): Option[OverviewUpload] = withPgConnection { implicit c =>
    LO.withLargeObject(upload.contentsOid) { lo => upload.withUploadedBytes(lo.add(chunk)).save }
  }

  /**
   * Duplicates functionality in TransactionActionController, but in a way that
   * enables us to get a hold of a PGConnection.
   * DB.withConnection gives us a Play AutoCleanConnection, which we can't cast.
   * DB.getConnection gives us a BoneCP ConnectionHandle, which can
   * be converted to the PGConnection we need for dealing with Postgres 
   * LargeObjects.
   */
  private def withPgConnection[A](f: PGConnection => A) = {
    val connection = DB.getConnection(autocommit = false)
    try {
      val adapter = new SquerylPostgreSqlAdapter()
      val session = new Session(connection, adapter)
      using(session) {
        val connectionHandle = connection.asInstanceOf[ConnectionHandle]
        val pgConnection = connectionHandle.getInternalConnection.asInstanceOf[PGConnection]

        val r = f(pgConnection)
        connection.commit      // simply closing the connection does not seem to commit the transaction.
        r
      }
    } finally {
      connection.close
    }
  }

}

