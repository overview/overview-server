package models.orm

import anorm.SQL
import org.squeryl.KeyedEntity

class DocumentSet(val query: String) extends KeyedEntity[Long] {
  override val id: Long = 0

  lazy val users = Schema.documentSetUsers.left(this)

  // It's one-to-one, which in the DB is one-to-many. This method is an
  // implementation detail, but Squeryl won't let us make it private.
  lazy val documentSetCreationJobs = Schema.documentSetDocumentSetCreationJobs.left(this)

  def documentSetCreationJob = documentSetCreationJobs.headOption;

  def buildDocumentSetCreationJob() = new DocumentSetCreationJob(id)

  def createDocumentSetCreationJob() = Schema.documentSetCreationJobs.insert(buildDocumentSetCreationJob)
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
}
