package models.orm

import anorm.SQL
import org.squeryl.KeyedEntity

class DocumentSet(val query: String) extends KeyedEntity[Long] {
  override val id: Long = 0

  lazy val users = Schema.documentSetUsers.left(this)
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
