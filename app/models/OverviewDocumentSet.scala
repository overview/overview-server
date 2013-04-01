package models

import org.overviewproject.tree.orm.{ Document, DocumentSetCreationJob, UploadedFile }
import org.overviewproject.tree.orm.DocumentSetCreationJobType._

import org.overviewproject.tree.Ownership
import models.orm.{ DocumentSet, DocumentSetType, DocumentSetUser, User }
import models.upload.OverviewUploadedFile
import models.orm.finders.DocumentSetFinder

trait OverviewDocumentSet {
  /** database ID */
  val id: Long

  /**
    * Number of documents.
    *
    * If the DocumentSet hasn't finished being generated, this number may be
    * less than its final value.
    */
  val documentCount: Int

  /** Number of documents we couldn't import. */
  val documentProcessingErrorCount: Int

  /** true if the document set is public */
  val isPublic: Boolean

  /** Title of the document set. (Empty string is allowed.) */
  val title: String

  /** Creation date (without milliseconds) */
  val createdAt: java.util.Date

  /** The user owning the document set */
  val owner: OverviewUser

  /** FIXME: Only here because admin page expects it of all jobs */
  val query: String

  /**
    * @return a new OverviewDocumentSet owned by cloneOwner. Creates a OverviewDocumentSetCreationJob
    * that will create a copy of the original, including nodes, tags, and documents.
    */
  def cloneForUser(cloneOwnerId: Long): OverviewDocumentSet

  /** Add a viewer to the document set */
  def setUserRole(email: String, role: Ownership.Value): Unit

  /** Remove the viewer */
  def removeViewer(email: String): Unit
}

object OverviewDocumentSet {
  import scala.language.postfixOps

  trait OverviewDocumentSetImpl extends OverviewDocumentSet {
    import models.orm.Schema
    import org.overviewproject.postgres.SquerylEntrypoint._
    
    protected val ormDocumentSet: DocumentSet

    override val id = ormDocumentSet.id
    override val documentCount = ormDocumentSet.documentCount.toInt
    override val documentProcessingErrorCount = ormDocumentSet.documentProcessingErrorCount.toInt
    override val isPublic = ormDocumentSet.isPublic
    override val title = ormDocumentSet.title
    override val createdAt = ormDocumentSet.createdAt
    override lazy val owner = findOwner

    override lazy val query = ""

    override def cloneForUser(cloneOwnerId: Long): OverviewDocumentSet = {
      val ormDocumentSetClone = cloneDocumentSet.save
      val documentSetClone = OverviewDocumentSet(ormDocumentSetClone)
      
      User.findById(cloneOwnerId).map { u =>
        documentSetClone.setUserRole(u.email, Ownership.Owner)
      }

      val cloneJob = DocumentSetCreationJob(documentSetCreationJobType = CloneJob, documentSetId = ormDocumentSetClone.id, sourceDocumentSetId = Some(ormDocumentSet.id))
      Schema.documentSetCreationJobs.insert(cloneJob)
      documentSetClone
    }

    override def setUserRole(email: String, role: Ownership.Value): Unit = {
      val emailWithRole = Schema.documentSetUsers.where(dsu => dsu.documentSetId === id and dsu.userEmail === email).headOption
      emailWithRole match {
        case Some(u) if (u.role != Ownership.Owner) => Schema.documentSetUsers.update(u.copy(role = role))
        case Some(u) if (u.role == Ownership.Owner) => // Owner can't change, for now.
        case _ => Schema.documentSetUsers.insert(DocumentSetUser(id, email, role))
      }
    }

    override def removeViewer(email: String): Unit =  Schema.documentSetUsers.deleteWhere(dsu => dsu.documentSetId === id and dsu.userEmail === email)

    protected def cloneDocumentSet: DocumentSet = ormDocumentSet.copy(id = 0, isPublic = false, createdAt = new java.sql.Timestamp(scala.compat.Platform.currentTime))

    private def findOwner: OverviewUser = {
      import models.orm.Schema.documentSetUsers
      import org.overviewproject.postgres.SquerylEntrypoint._

      val documentUsers = Schema.documentSetUsers.where(dsu => dsu.documentSetId === id)
      val ownerEmail = documentUsers.filter(_.role == Ownership.Owner).head.userEmail
      
      OverviewUser.findByEmail(ownerEmail).get
    }
  }

  case class CsvImportDocumentSet(
    protected val ormDocumentSet: DocumentSet,
    // providedUploadedFile is None if not provided, Some(None) if empty, Some(something) if set
    protected val providedUploadedFile: Option[Option[OverviewUploadedFile]])
    extends OverviewDocumentSetImpl {

    lazy val uploadedFile: Option[OverviewUploadedFile] =
      providedUploadedFile.getOrElse(
        ormDocumentSet.withUploadedFile.uploadedFile.map(OverviewUploadedFile.apply)
      )

    override protected def cloneDocumentSet: DocumentSet = {
      val ormDocumentSetClone = super.cloneDocumentSet
      val uploadedFileClone = ormDocumentSetClone.withUploadedFile.uploadedFile.map(f => OverviewUploadedFile(f.copy(id = 0)).save)
      ormDocumentSetClone.copy(uploadedFileId = uploadedFileClone.map(_.id))
    }
  }

  case class DocumentCloudDocumentSet(protected val ormDocumentSet: DocumentSet) extends OverviewDocumentSetImpl {
    private def throwOnNull = throw new Exception("DocumentCloudDocumentSet has NULL values it should not have")

    override lazy val query: String = ormDocumentSet.query.getOrElse(throwOnNull)
  }

  /** Factory method */
  def apply(ormDocumentSet: DocumentSet): OverviewDocumentSet = {
    ormDocumentSet.documentSetType match {
      case DocumentSetType.CsvImportDocumentSet => CsvImportDocumentSet(ormDocumentSet, None)
      case DocumentSetType.DocumentCloudDocumentSet => DocumentCloudDocumentSet(ormDocumentSet)
      case _ => throw new Exception("Impossible document-set type " + ormDocumentSet.documentSetType.value)
    }
  }

  /** Factory method, with uploadedFile set */
  def apply(ormDocumentSet: DocumentSet, ormUploadedFile: Option[UploadedFile]): OverviewDocumentSet = {
    ormDocumentSet.documentSetType match {
      case DocumentSetType.CsvImportDocumentSet => CsvImportDocumentSet(
        ormDocumentSet, Some(ormUploadedFile.map(OverviewUploadedFile.apply)))
      case DocumentSetType.DocumentCloudDocumentSet => DocumentCloudDocumentSet(ormDocumentSet)
      case _ => throw new Exception("Impossible document-set type " + ormDocumentSet.documentSetType.value)
    }
  }

  /** Database lookup */
  def findById(id: Long): Option[OverviewDocumentSet] = {
    DocumentSetFinder.byDocumentSet(id).headOption.map(apply)
  }

  /** @return ResultPage of all document sets the user owns */
  def findByUserId(userEmail: String, pageSize: Int, page: Int): ResultPage[OverviewDocumentSet] = {
    val documentSets = DocumentSetFinder.byOwner(userEmail)
    ResultPage(documentSets, pageSize, page).map { ds : DocumentSet => apply(ds, None) }
  }

  /** @return All document sets marked public */
  def findPublic: Iterable[OverviewDocumentSet] = {
    import org.overviewproject.postgres.SquerylEntrypoint._
    DocumentSetFinder.byIsPublic(true).map(OverviewDocumentSet.apply)
  }

  /** Delete the document set */
  def delete(id: Long) {
    cancelCloneJobs(id)
    deleteClientGeneratedInformation(id)
    val cancelledJob = OverviewDocumentSetCreationJob.cancelJobWithDocumentSetId(id)
    if (!cancelledJob.isDefined) deleteClusteringGeneratedInformation(id)
  }

  /** @return all email addresses with the role `Viewer` for a document set
    * @param id The id of the document set
    */
  def findViewers(id: Long): Iterable[DocumentSetUser] = {
    import models.orm.Schema.documentSetUsers
    import org.overviewproject.postgres.SquerylEntrypoint._

    documentSetUsers.where(dsu => dsu.documentSetId === id).filter(_.role == Ownership.Viewer)
  }
  
  /** @return all document sets that have been shared with the specified user
    */
  def findByViewer(email: String): Iterable[OverviewDocumentSet] = {
    import org.overviewproject.postgres.SquerylEntrypoint._
    DocumentSetFinder.byViewer(email).map(OverviewDocumentSet.apply)
  }

  private def deleteClientGeneratedInformation(id: Long) {
    import models.orm.Schema._
    import org.overviewproject.postgres.SquerylEntrypoint._

    logEntries.deleteWhere(le => le.documentSetId === id)
    documentTags.deleteWhere(nt =>
      nt.tagId in from(tags)(t => where(t.documentSetId === id) select (t.id)))
    tags.deleteWhere(t => t.documentSetId === id)
    documentSetUsers.deleteWhere(du => du.documentSetId === id)
  }

  private def deleteClusteringGeneratedInformation(id: Long) = {
    import anorm._
    import anorm.SqlParser._
    import models.orm.Schema._
    import org.overviewproject.postgres.SquerylEntrypoint._
    implicit val connection = OverviewDatabase.currentConnection

    SQL("SELECT lo_unlink(contents_oid) FROM document_set_creation_job WHERE document_set_id = {id} AND contents_oid IS NOT NULL").on('id -> id).as(scalar[Int] *)

    documentSetCreationJobs.deleteWhere(dscj => dscj.documentSetId === id)

    SQL("""
        DELETE FROM node_document WHERE node_id IN (
          SELECT id FROM node WHERE document_set_id = {id}
        )""").on('id -> id).executeUpdate()

    documents.deleteWhere(d => d.documentSetId === id)
    documentProcessingErrors.deleteWhere(dpe => dpe.documentSetId === id)
    nodes.deleteWhere(n => n.documentSetId === id)

    // use headOption rather than single to handle case where uploadedFileId is deleted already
    // flatMap identity to transform from Option[Option[Long]] to Option[Long]
    val uploadedFileId = from(documentSets)(d => where(d.id === id) select (d.uploadedFileId)).headOption.flatMap(identity)
    documentSets.delete(id)

    uploadedFileId.map { uid => uploadedFiles.deleteWhere(f => f.id === uid) }
  }

  private def cancelCloneJobs(sourceId: Long): Unit = {
    val cloneJobs = OverviewDocumentSetCreationJob.cancelJobsWithSourceDocumentSetId(sourceId)
    cloneJobs.foreach(j => deleteClientGeneratedInformation(j.documentSetId))
  }
}
