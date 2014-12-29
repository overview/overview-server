package controllers.backend

import java.util.UUID
import org.postgresql.PGConnection
import scala.concurrent.Future

import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.models.tables.GroupedFileUploads
import org.overviewproject.postgres.LO

trait GroupedFileUploadBackend extends Backend {
  /** Finds or creates a GroupedFileUpload.
    *
    * If the guid matches an existing guid in the given FileGroup, this will
    * return the existing GroupedFileUpload (unmodified). Otherwise, it will
    * return a new GroupedFileUpload.
    *
    * XXX This method has a race when called twice simultaneously with the same
    * fileGroupId and guid. It will throw an exception. We should leave it
    * uncaught so we get emailed if this ever happens.
    */
  def findOrCreate(attributes: GroupedFileUpload.CreateAttributes): Future[GroupedFileUpload]

  /** Finds a GroupedFileUpload.
    */
  def find(fileGroupId: Long, guid: UUID): Future[Option[GroupedFileUpload]]

  /** Writes bytes to a GroupedFileUpload.
    *
    * This updates the <tt>uploadedSize</tt> of the GroupedFileUpload.
    *
    * You get undefined behavior if you write past the end of the large object.
    *
    * FIXME make this a separate backend.
    *
    * @param id The LargeObject id. <em>NOT</em> the GroupedFileUpload ID!
    * @param position Position to start writing.
    * @param bytes Bytes to write.
    */
  def writeBytes(loid: Long, position: Long, bytes: Array[Byte]): Future[Unit]
}

trait DbGroupedFileUploadBackend extends GroupedFileUploadBackend { self: DbBackend =>
  import org.overviewproject.database.Slick.simple._

  lazy val byFileGroupAndGuidCompiled = Compiled { (fileGroupId: Column[Long], guid: Column[UUID]) =>
    GroupedFileUploads
      .filter(_.fileGroupId === fileGroupId)
      .filter(_.guid === guid)
  }

  lazy val insertInvoker = (GroupedFileUploads.map(gfu => (
    gfu.fileGroupId,
    gfu.guid,
    gfu.contentType,
    gfu.name,
    gfu.size,
    gfu.uploadedSize,
    gfu.contentsOid
  )) returning GroupedFileUploads).insertInvoker

  def find(fileGroupId: Long, guid: UUID) = db { session =>
    byFileGroupAndGuidCompiled(fileGroupId, guid).firstOption(session)
  }

  private def withPgConnection[A](session: Session)(block: PGConnection => A) = {
    // Large Object stuff _must_ be done within a transaction, or Postgres
    // won't do it.
    //
    // TODO think through transactions a bit more
    import org.overviewproject.database.DB

    val wasAutoCommit = session.conn.getAutoCommit

    try {
      session.conn.setAutoCommit(false)
      val pgConnection = DB.pgConnection(session.conn)
      block(pgConnection)
    } finally {
      session.conn.setAutoCommit(wasAutoCommit)
    }
  }

  private def create(attributes: GroupedFileUpload.CreateAttributes, session: Session) = {
    withPgConnection(session) { pgConnection =>
      LO.withLargeObject({ largeObject =>
        insertInvoker.insert(
          attributes.fileGroupId,
          attributes.guid,
          attributes.contentType,
          attributes.name,
          attributes.size,
          0L,
          largeObject.oid
        )(session)
      })(pgConnection)
    }
  }

  def findOrCreate(attributes: GroupedFileUpload.CreateAttributes) = db { session =>
    byFileGroupAndGuidCompiled(attributes.fileGroupId, attributes.guid).firstOption(session)
      .getOrElse(create(attributes, session))
  }

  def writeBytes(loid: Long, position: Long, bytes: Array[Byte]) = db { session =>
    withPgConnection(session) { pgConnection =>
      LO.withLargeObject(loid)({ largeObject =>
        largeObject.insert(bytes, position.toInt)
      })(pgConnection)
    }
  }
}

object GroupedFileUploadBackend extends DbGroupedFileUploadBackend with DbBackend
