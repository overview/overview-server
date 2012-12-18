/*
 * DocumentSet.scala
 *
 * Overview Project
 * Created by Adam Hooper, Aug 2012
 */
package models.orm

import anorm.SQL
import anorm.SqlParser._
import java.util.Date
import java.sql.Timestamp
import org.squeryl.dsl.OneToMany
import org.squeryl.{KeyedEntity,Query}
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.annotations.{Column,Transient}
import scala.annotation.target.field

import org.overviewproject.postgres.PostgresqlEnum
import org.overviewproject.tree.orm.DocumentSetCreationJob
import models.OverviewDatabase

class DocumentSetType(v: String) extends PostgresqlEnum(v, "document_set_type")

object DocumentSetType {
  val DocumentCloudDocumentSet = new DocumentSetType("DocumentCloudDocumentSet")
  val CsvImportDocumentSet = new DocumentSetType("CsvImportDocumentSet")
}

import DocumentSetType._

case class DocumentSet(
    @Column("type") val documentSetType: DocumentSetType,
    override val id: Long = 0,
    val title: String = "",
    val query: Option[String] = None,
    @Column("created_at") val createdAt: Timestamp = new Timestamp((new Date()).getTime),
    @Column("uploaded_file_id") val uploadedFileId: Option[Long] = None,
    @(Transient @field) val providedDocumentCount: Option[Long] = None,
    @(Transient @field) val documentSetCreationJob: Option[DocumentSetCreationJob] = None,
    @(Transient @field) val uploadedFile: Option[UploadedFile] = None
    ) extends KeyedEntity[Long] {

  def this() = this(documentSetType = DocumentCloudDocumentSet) // For Squeryl
  
  lazy val users = Schema.documentSetUsers.left(this)

  lazy val documents = Schema.documentSetDocuments.left(this)

  lazy val nodes = Schema.documentSetNodes.left(this)

  lazy val logEntries = Schema.documentSetLogEntries.left(this)

  lazy val orderedLogEntries = from(logEntries)(le => select(le).orderBy(le.date desc))

  /**
   * Create a new DocumentSetCreationJob for the document set.
   *
   * The job will be inserted into the database in the state NotStarted.
   *
   * Should only be called after the document set has been inserted into the database.
   */
  def createDocumentSetCreationJob(username: Option[String]=None, password: Option[String]=None): DocumentSetCreationJob = {
    require(id != 0l)
    val documentSetCreationJob = new DocumentSetCreationJob(id, documentcloudUsername=username, documentcloudPassword=password)
    Schema.documentSetDocumentSetCreationJobs.left(this).associate(documentSetCreationJob)
  }

  def withCreationJob = copy(documentSetCreationJob =
    Schema.documentSetDocumentSetCreationJobs.left(this).headOption
  )

  def withUploadedFile = copy(uploadedFile =
    Schema.uploadedFileDocumentSets.right(this).headOption
  )

  def documentCount : Long = {
    providedDocumentCount.getOrElse(
      from(Schema.documents)(d => where(d.documentSetId === this.id) compute(count)).single.measures
    )
  }

  // https://www.assembla.com/spaces/squeryl/tickets/68-add-support-for-full-updates-on-immutable-case-classes#/followers/ticket:68
  override def isPersisted(): Boolean = (id > 0)

  def save: DocumentSet = Schema.documentSets.insertOrUpdate(this)
}

object DocumentSet {
  def findById(id: Long) = Schema.documentSets.lookup(id)

  def findByUserIdOrderedByCreatedAt(userId: Long) : Query[DocumentSet] = {
    from(Schema.documentSetUsers.thisTable, Schema.documentSets)((dsu, ds) =>
      where(dsu.documentSetId === ds.id and dsu.userId === userId)
      select(ds)
      orderBy(ds.createdAt.desc)
    )
  }

  /** Deletes a DocumentSet by ID.
    *
    * @return true if the deletion succeeded, false if it failed.
    */
  def delete(id: Long) : Boolean = {
    implicit val connection = OverviewDatabase.currentConnection

    val documentSet = DocumentSet.findById(id)
    val uploadedFileId = documentSet.map(_.uploadedFileId)
    
    SQL("DELETE FROM log_entry WHERE document_set_id = {id}").on('id -> id).executeUpdate()
    SQL("DELETE FROM document_tag WHERE tag_id IN (SELECT id FROM tag WHERE document_set_id = {id})").on('id -> id).executeUpdate()
    SQL("DELETE FROM tag WHERE document_set_id = {id}").on('id -> id).executeUpdate()
    SQL("DELETE FROM node_document WHERE node_id IN (SELECT id FROM node WHERE document_set_id = {id})").on('id -> id).executeUpdate()
    SQL("DELETE FROM node WHERE document_set_id = {id}").on('id -> id).executeUpdate()
    SQL("DELETE FROM document WHERE document_set_id = {id}").on('id -> id).executeUpdate()
    SQL("DELETE FROM document_set_user WHERE document_set_id = {id}").on('id -> id).executeUpdate()

    Schema.documentSetCreationJobs.deleteWhere(dscj => dscj.documentSetId === id)

    val success = Schema.documentSets.delete(id)

    uploadedFileId.map { u => 
      SQL("SELECT lo_unlink(contents_oid) FROM uploaded_file WHERE id = {id}").on('id -> u).as(scalar[Int] *)
      SQL("DELETE FROM uploaded_file WHERE id = {id}").on('id -> u).executeUpdate()
    }

    success
  }

  private def findIdToDocumentCountMap(ids: Seq[Long]) : Map[Long,Long] = {
    from(Schema.documents)(d =>
      where(d.documentSetId in ids)
      groupBy(d.documentSetId)
      compute(count)
    ).map(g => g.key -> g.measures).toMap
  }

  private def findIdToDocumentSetCreationJobMap(ids: Seq[Long]) : Map[Long,DocumentSetCreationJob] = {
    from(Schema.documentSetCreationJobs)(j =>
      where(j.documentSetId in ids).select(j)
    ).map(dscj => dscj.documentSetId -> dscj).toMap
  }

  private def findIdToUploadedFileMap(ids: Seq[Long]) : Map[Long,UploadedFile] = {
    from(Schema.uploadedFiles)(uf =>
      where(uf.id in ids).select(uf)
    ).map(uf => uf.id -> uf).toMap
  }

  def addDocumentCounts(documentSets: Seq[DocumentSet]) : Seq[DocumentSet] = {
    val ids = documentSets.map(_.id)
    val counts = findIdToDocumentCountMap(ids)
    documentSets.map(ds => ds.copy(providedDocumentCount=counts.get(ds.id)))
  }

  def addCreationJobs(documentSets: Seq[DocumentSet]) : Seq[DocumentSet] = {
    val ids = documentSets.map(_.id)
    val creationJobs = findIdToDocumentSetCreationJobMap(ids)
    documentSets.map(ds => ds.copy(documentSetCreationJob=creationJobs.get(ds.id)))
  }

  def addUploadedFiles(documentSets: Seq[DocumentSet]) : Seq[DocumentSet] = {
    val optionalIds : Seq[Option[Long]] = documentSets.map(_.uploadedFileId)
    val ids : Seq[Long] = optionalIds.flatten
    if (ids.length > 0) {
      val uploadedFiles = findIdToUploadedFileMap(ids)
      documentSets.map(ds => ds.copy(uploadedFile = ds.uploadedFileId.flatMap(uploadedFiles.get)))
    } else {
      documentSets
    }
  }

  object ImplicitHelper {
    class DocumentSetSeq(documentSets: Seq[DocumentSet]) {
      def withDocumentCounts = DocumentSet.addDocumentCounts(documentSets)
      def withCreationJobs = DocumentSet.addCreationJobs(documentSets)
      def withUploadedFiles = DocumentSet.addUploadedFiles(documentSets)
    }

    implicit def seqDocumentSetToDocumentSetSeq(documentSets: Seq[DocumentSet]) = new DocumentSetSeq(documentSets)
  }
}
