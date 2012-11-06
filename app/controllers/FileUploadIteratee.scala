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
import play.api.mvc.Results.{ BadRequest, InternalServerError }
import play.api.Play.current
import play.api.libs.iteratee.Done
import play.api.libs.iteratee.Input

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
      def defaultContentRange(length: String) = "0-%1$s/%1$s".format(length)

      for {
        contentDisposition <- header.headers.get("CONTENT-DISPOSITION")
        contentLength <- header.headers.get("CONTENT-LENGTH")
        contentRange <- header.headers.get("CONTENT-RANGE").orElse(Some(defaultContentRange(contentLength)))
      } yield {
        val disposition = "[^=]*=\"?([^\"]*)\"?".r // attachment ; filename="foo.bar" (optional quotes) TODO: Handle quoted quotes
        val disposition(filename) = contentDisposition
        val range = """(\d+)-(\d+)/\d+""".r
        val range(start, end) = contentRange
        UploadInfo(filename, start.toLong, contentLength.toLong)
      }
    }
  }

  /**
   * Checks the validity of the requests and processes the upload.
   */
  def store(userId: Long, guid: UUID, requestHeader: RequestHeader): Iteratee[Array[Byte], Either[Result, OverviewUpload]] = {

    val uploadInfo = UploadInfo(requestHeader).toRight(BadRequest)

    uploadInfo.fold(
      r => Done(Left(r), Input.Empty),
      info => {
        val upload = findUpload(userId, guid).orElse(createUpload(userId, guid, info.filename, info.contentLength))

        val validUploadRestart = upload.map(u => 
          info.start match {
            case 0 => Right(u.truncate)
            case n if n == u.bytesUploaded => Right(u)
            case _ => Left(BadRequest)
          })

        val initialUpload = validUploadRestart.getOrElse(createUpload(userId, guid, info.filename, info.contentLength).toRight(InternalServerError))

        Iteratee.fold[Array[Byte], Either[Result, OverviewUpload]](initialUpload) { (upload, chunk) =>
          upload.right.map(appendChunk(_, chunk).get)
        }
      })
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
        connection.commit // simply closing the connection does not seem to commit the transaction.
        r
      }
    } finally {
      connection.close
    }
  }

}

