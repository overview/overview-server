/*
 * DocumentSet.scala
 *
 * Overview Project
 * Created by Adam Hooper, Aug 2012
 */
package models.orm

import anorm.SQL
import org.squeryl.dsl.OneToMany
import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.annotations.Transient
import scala.annotation.target.field

case class DocumentSet(
    val id: Long = 0,
    val title: String = "",
    val query: String = "",
    @(Transient @field)
    val providedDocumentCount: Option[Long] = None,
    @(Transient @field)
    val documentSetCreationJob: Option[DocumentSetCreationJob] = None
    ) extends KeyedEntity[Long] {
  lazy val users = Schema.documentSetUsers.left(this)

  lazy val documents = Schema.documentSetDocuments.left(this)

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
    val documentSetCreationJob = new DocumentSetCreationJob(id, username=username, password=password)
    Schema.documentSetDocumentSetCreationJobs.left(this).associate(documentSetCreationJob)
  }

  def withCreationJob = copy(documentSetCreationJob =
    Schema.documentSetDocumentSetCreationJobs.left(this).headOption
  )

  def documentCount : Long = {
    providedDocumentCount.getOrElse(
      from(Schema.documents)(d => where(d.documentSetId === this.id) compute(count)).single.measures
    )
  }

  def save(): DocumentSet = {
    require(id == 0L)

    Schema.documentSets.insertOrUpdate(this)
  }
}

object DocumentSet {
  def delete(id: Long)(implicit connection: java.sql.Connection) = {
    SQL("DELETE FROM log_entry WHERE document_set_id = {id}").on('id -> id).executeUpdate()
    SQL("DELETE FROM document_tag WHERE tag_id IN (SELECT id FROM tag WHERE document_set_id = {id})").on('id -> id).executeUpdate()
    SQL("DELETE FROM tag WHERE document_set_id = {id}").on('id -> id).executeUpdate()
    SQL("DELETE FROM node_document WHERE node_id IN (SELECT id FROM node WHERE document_set_id = {id})").on('id -> id).executeUpdate()
    SQL("DELETE FROM node WHERE document_set_id = {id}").on('id -> id).executeUpdate()
    SQL("DELETE FROM document WHERE document_set_id = {id}").on('id -> id).executeUpdate()
    SQL("DELETE FROM document_set_user WHERE document_set_id = {id}").on('id -> id).executeUpdate()
    Schema.documentSetCreationJobs.deleteWhere(dscj => dscj.documentSetId === id)
    Schema.documentSets.delete(id)
    // And return the count
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

  object ImplicitHelper {
    class DocumentSetSeq(documentSets: Seq[DocumentSet]) {
      def withDocumentCounts = DocumentSet.addDocumentCounts(documentSets)
      def withCreationJobs = DocumentSet.addCreationJobs(documentSets)
    }

    implicit def seqDocumentSetToDocumentSetSeq(documentSets: Seq[DocumentSet]) = new DocumentSetSeq(documentSets)
  }
}
