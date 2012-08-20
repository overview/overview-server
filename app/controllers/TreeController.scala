package controllers

import play.api.mvc._
import play.api.libs.json.Json._
import java.sql.Connection
import anorm.SQL
import anorm.SqlParser.scalar

import models.SubTreeLoader

object TreeController extends BaseController {
  def root(id: Long) = authorizedAction(userOwningDocumentSet(id)) { user => (request: Request[AnyContent], connection: Connection) => authorizedRoot(user, id)(request, connection) }
  def node(id: Long, nodeId: Long) = authorizedAction(userOwningDocumentSet(id)) { user => (request: Request[AnyContent], connection: Connection) => authorizedNode(user, id, nodeId)(request, connection) }

  private def authorizedRoot(user: User, id: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    // FIXME move this to model
    val rootId = SQL("""
        SELECT id FROM node
        WHERE document_set_id = {document_set_id} AND parent_id IS NULL
        """).on('document_set_id -> id).as(scalar[Long].single)

    val subTreeLoader = new SubTreeLoader(rootId, 4)
    val nodes = subTreeLoader.loadNodes
    val documents = subTreeLoader.loadDocuments(nodes)
    val tags = subTreeLoader.loadTags(id)
    
    val json = views.json.Tree.show(nodes, documents, tags)
    Ok(json)
  }

  private def authorizedNode(user: User, id: Long, nodeId: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    val subTreeLoader = new SubTreeLoader(nodeId, 4)
    val nodes = subTreeLoader.loadNodes
    val documents = subTreeLoader.loadDocuments(nodes)
    val tags = subTreeLoader.loadTags(id)
    
    val json = views.json.Tree.show(nodes, documents, tags)
    Ok(json)
  }
}
