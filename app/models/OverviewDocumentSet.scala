package models

import org.overviewproject.tree.orm.{ Document, DocumentSetCreationJob }
import models.orm.DocumentSet
import models.upload.OverviewUploadedFile

trait OverviewDocumentSet {
  /** database ID */
  val id: Long

  /*
   * XXX we don't have a "list documents" method. To include one, we'd need
   * an API for filtering and pagination--wrappers around Squeryl features.
   */

  /**
   * Creation job, if this DocumentSet isn't complete yet.
   * FIXME: should be models.OverviewDocumentSetCreationJob, but we don't have one
   */
  def creationJob: Option[OverviewDocumentSetCreationJob]

  /**
   * Number of documents.
   *
   * If the DocumentSet hasn't finished being generated, this number may be
   * less than its final value.
   */
  def documentCount: Int

  /** Title of the document set. (Empty string is allowed.) */
  val title: String

  /** Creation date (without milliseconds) */
  val createdAt: java.util.Date

  /** The user owning the document set */
  val user: OverviewUser

  /** FIXME: Only here because admin page expects it of all jobs */
  val query: String
}

object OverviewDocumentSet {
  trait OverviewDocumentSetImpl extends OverviewDocumentSet {
    protected val ormDocumentSet: DocumentSet

    override val id = ormDocumentSet.id
    override lazy val creationJob = OverviewDocumentSetCreationJob.findByDocumentSetId(id)
    override lazy val documentCount = ormDocumentSet.documentCount.toInt
    override val title = ormDocumentSet.title
    override val createdAt = ormDocumentSet.createdAt
    override lazy val user = {
      OverviewUser.findById(ormDocumentSet.users.single.id).get
    }
    override lazy val query = ""
  }

  case class CsvImportDocumentSet(protected val ormDocumentSet: DocumentSet) extends OverviewDocumentSetImpl {
    lazy val uploadedFile: Option[OverviewUploadedFile] =
      ormDocumentSet.uploadedFile.map(OverviewUploadedFile.apply)
  }

  case class DocumentCloudDocumentSet(protected val ormDocumentSet: DocumentSet) extends OverviewDocumentSetImpl {
    private def throwOnNull = throw new Exception("DocumentCloudDocumentSet has NULL values it should not have")

    override lazy val query: String = ormDocumentSet.query.getOrElse(throwOnNull)
  }

  /** Factory method */
  def apply(ormDocumentSet: DocumentSet): OverviewDocumentSet = {
    ormDocumentSet.documentSetType.value match {
      case "CsvImportDocumentSet" => CsvImportDocumentSet(ormDocumentSet)
      case "DocumentCloudDocumentSet" => DocumentCloudDocumentSet(ormDocumentSet)
      case _ => throw new Exception("Impossible document-set type " + ormDocumentSet.documentSetType.value)
    }
  }

  /** Database lookup */
  def findById(id: Long): Option[OverviewDocumentSet] = {
    DocumentSet.findById(id).map({ ormDocumentSet =>
      OverviewDocumentSet(ormDocumentSet.withUploadedFile.withCreationJob)
    })
  }

  def delete(id: Long) {
    import models.orm.Schema._
    import org.squeryl.PrimitiveTypeMode._
    import org.overviewproject.tree.orm.DocumentSetCreationJobState._

    deleteClientGeneratedInformation(id)
    val cancelledJob = OverviewDocumentSetCreationJob.cancelJobWithDocumentSetId(id)
    if (!cancelledJob.isDefined) deleteClusteringGeneratedInformation(id)
  }

  private def deleteClientGeneratedInformation(id: Long) {
    import models.orm.Schema._
    import org.squeryl.PrimitiveTypeMode._

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
    import org.squeryl.PrimitiveTypeMode._
    implicit val connection = OverviewDatabase.currentConnection
    
    documentSetCreationJobs.deleteWhere(dscj => dscj.documentSetId === id)
    
    SQL("""
        DELETE FROM node_document WHERE node_id IN (
          SELECT id FROM node WHERE document_set_id = {id}
        )""").on('id -> id).executeUpdate()

    documents.deleteWhere(d => d.documentSetId === id)
    nodes.deleteWhere(n => n.documentSetId === id)

    val uploadedFileId = from(documentSets)(d => where(d.id === id) select (d.uploadedFileId)).single
    documentSets.delete(id)
    
    uploadedFileId.map { u =>
      SQL("SELECT lo_unlink(contents_oid) FROM uploaded_file WHERE id = {id}").on('id -> u).as(scalar[Int] *)
      uploadedFiles.deleteWhere(f => f.id === u)
    }

  }
}
