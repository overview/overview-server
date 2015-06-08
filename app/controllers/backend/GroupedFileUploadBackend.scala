package controllers.backend

import java.util.UUID
import org.postgresql.PGConnection
import scala.concurrent.Future

import org.overviewproject.database.LargeObject
import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.models.tables.GroupedFileUploads

trait GroupedFileUploadBackend extends Backend {
  /** Lists GroupedFileUploads in a FileGroup.
    */
  def index(fileGroupId: Long): Future[Seq[GroupedFileUpload]]

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
    * @param id The GruopedFileUpload id.
    * @param position Position to start writing.
    * @param bytes Bytes to write.
    */
  def writeBytes(id: Long, position: Long, bytes: Array[Byte]): Future[Unit]
}

trait DbGroupedFileUploadBackend extends GroupedFileUploadBackend with DbBackend {
  import databaseApi._

  private implicit val ec = database.executionContext

  lazy val byFileGroupId = Compiled { (fileGroupId: Rep[Long]) =>
    GroupedFileUploads.filter(_.fileGroupId === fileGroupId)
  }

  lazy val byFileGroupAndGuidCompiled = Compiled { (fileGroupId: Rep[Long], guid: Rep[UUID]) =>
    GroupedFileUploads
      .filter(_.fileGroupId === fileGroupId)
      .filter(_.guid === guid)
  }

  lazy val inserter = (GroupedFileUploads.map(gfu => (
    gfu.fileGroupId,
    gfu.guid,
    gfu.contentType,
    gfu.name,
    gfu.size,
    gfu.uploadedSize,
    gfu.contentsOid
  )) returning GroupedFileUploads)

  lazy val updateUploadedSizeByIdCompiled = Compiled { (id: Rep[Long]) =>
    GroupedFileUploads.filter(_.id === id).map(_.uploadedSize)
  }

  lazy val loidByIdCompiled = Compiled { (id: Rep[Long]) =>
    GroupedFileUploads.filter(_.id === id).map(_.contentsOid)
  }

  override def index(fileGroupId: Long) = database.seq(byFileGroupId(fileGroupId))

  override def find(fileGroupId: Long, guid: UUID) = {
    database.option(byFileGroupAndGuidCompiled(fileGroupId, guid))
  }

  private def create(attributes: GroupedFileUpload.CreateAttributes): Future[GroupedFileUpload] = {
    val action = for {
      oid <- database.largeObjectManager.create
      groupedFileUpload <- inserter.+=(
        attributes.fileGroupId,
        attributes.guid,
        attributes.contentType,
        attributes.name,
        attributes.size,
        0L,
        oid
      )
    } yield groupedFileUpload

    database.run(action.transactionally)
  }

  override def findOrCreate(attributes: GroupedFileUpload.CreateAttributes) = {
    find(attributes.fileGroupId, attributes.guid)
      .flatMap(_ match {
        case Some(fileGroup) => Future.successful(fileGroup)
        case None => create(attributes)
      })
  }

  def writeBytes(id: Long, position: Long, bytes: Array[Byte]) = {
    val action = for {
      oid <- loidByIdCompiled(id).result.head
      largeObject <- database.largeObjectManager.open(oid, LargeObject.Mode.Write)
      _ <- largeObject.seek(position)
      _ <- largeObject.write(bytes)
      _ <- updateUploadedSizeByIdCompiled(id).update(position + bytes.length)
    } yield ()

    database.run(action.transactionally)
  }
}

object GroupedFileUploadBackend
  extends DbGroupedFileUploadBackend
  with org.overviewproject.database.DatabaseProvider
