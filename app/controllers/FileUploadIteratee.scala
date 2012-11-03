package controllers

import com.jolbox.bonecp.ConnectionHandle
import java.util.UUID
import models.orm.SquerylPostgreSqlAdapter
import models.upload.LO
import models.upload.OverviewUpload
import org.postgresql.PGConnection
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Session
import play.api.db.DB
import play.api.libs.iteratee.{ Error, Input, Iteratee }
import play.api.mvc.Result
import play.api.mvc.Results._
import play.api.Play.current

trait FileUploadIteratee {

  def store(userId: Long, guid: UUID, filename: String, contentLength: Long): Iteratee[Array[Byte], Option[OverviewUpload]] = {
    val emptyUpload = createUpload(userId, guid, filename, contentLength)
    Iteratee.fold[Array[Byte], Option[OverviewUpload]](emptyUpload) { (upload, chunk) =>
      upload.flatMap(appendChunk(_, chunk))
    }
  }

  def createUpload(userId: Long, guid: UUID, filename: String, contentLength: Long): Option[OverviewUpload]

  def appendChunk(upload: OverviewUpload, chunk: Array[Byte]): Option[OverviewUpload]
}

object FileUploadIteratee extends FileUploadIteratee {

  def createUpload(userId: Long, guid: UUID, filename: String, contentLength: Long): Option[OverviewUpload] = withPgConnection { implicit c =>
    LO.withLargeObject { lo => OverviewUpload(userId, guid, filename, contentLength, lo.oid).save }
  }

  def appendChunk(upload: OverviewUpload, chunk: Array[Byte]): Option[OverviewUpload] = withPgConnection { implicit c =>
    LO.withLargeObject(upload.contentsOid) { lo =>  upload.withUploadedBytes(lo.add(chunk)).save }
  }

  private def withPgConnection[A](f: PGConnection => A) = {
    val connection = DB.getConnection(autocommit = false)
    try {
      val adapter = new SquerylPostgreSqlAdapter()
      val session = new Session(connection, adapter)
      using(session) {
        val connectionHandle = connection.asInstanceOf[ConnectionHandle]
        val pgConnection = connectionHandle.getInternalConnection.asInstanceOf[PGConnection]

        val r = f(pgConnection)
	connection.commit
	r
      }
    } finally {
      connection.close
    }
  }

}

