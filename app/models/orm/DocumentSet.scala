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


class DocumentSet(val query: String) extends KeyedEntity[Long] {
  override val id: Long = 0

  lazy val users = Schema.documentSetUsers.left(this)

  /**
   * It's one-to-one, which in the DB is one-to-many. This method is an
   * implementation detail, but Squeryl won't let us make it private.
   */
  lazy val documentSetCreationJobs: OneToMany[DocumentSetCreationJob] =
    Schema.documentSetDocumentSetCreationJobs.left(this)

  /**
   * The current DocumentSetCreationJob associated with the document set, 
   * or None if no association exists
   */
  def documentSetCreationJob: Option[DocumentSetCreationJob] =
    documentSetCreationJobs.headOption;

  /**
   * Create a new DocumentSetCreationJob for the document set. The job will be
   * inserted into the database in the state NotStarted. Should only be called
   * after the document set has been inserted into the database.x
   */
  def createDocumentSetCreationJob(): DocumentSetCreationJob = {
    require(id != 0l)
    val documentSetCreationJob = new DocumentSetCreationJob(id)
    documentSetCreationJobs.associate(documentSetCreationJob)
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
}
