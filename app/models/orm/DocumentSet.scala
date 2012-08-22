package models.orm

import anorm.SQL
import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.{Query,Queryable}
import org.squeryl.annotations.Transient
import scala.annotation.target.field

case class DocumentSet(
    val id: Long = 0,
    val query: String = "",
    @(Transient @field)
    val providedDocumentCount: Option[Long] = None,
    @(Transient @field)
    val documentSetCreationJob: Option[DocumentSetCreationJob] = None
    ) extends KeyedEntity[Long] {
  lazy val users = Schema.documentSetUsers.left(this)

  def withDocumentSetCreationJob = copy(documentSetCreationJob =
    Schema.documentSetDocumentSetCreationJobs.left(this).headOption
  )

  lazy val documents = Schema.documentSetDocuments.left(this)

  def documentCount : Long = {
    providedDocumentCount.getOrElse(
      from(Schema.documents)(d => where(d.documentSetId === this.id) compute(count)).single.measures
    )
  }

  def buildDocumentSetCreationJob() = new DocumentSetCreationJob(id)

  def createDocumentSetCreationJob() = Schema.documentSetCreationJobs.insert(buildDocumentSetCreationJob)

  def save() = Schema.documentSets.insertOrUpdate(this)
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
    SQL("DELETE FROM document_set WHERE id = {id}").on('id -> id).executeUpdate()
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
