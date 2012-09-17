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

    val subTreeLoader = new SubTreeLoader(id)
    
    subTreeLoader.loadRootId match {
      case Some(rootId) => {
        val nodes = subTreeLoader.load(rootId, 4)

        val tags = subTreeLoader.loadTags(id)
        val documents = subTreeLoader.loadDocuments(nodes, tags)

        val json = views.json.Tree.show(nodes, documents, tags)
        Ok(json)
      }
      case None => NotFound
    }
  }

  private def authorizedNode(user: User, id: Long, nodeId: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    val subTreeLoader = new SubTreeLoader(id)
    val nodes = subTreeLoader.load(nodeId, 2)
    val tags = subTreeLoader.loadTags(id)
    val documents = subTreeLoader.loadDocuments(nodes, tags)
    
    val json = views.json.Tree.show(nodes, documents, tags)
    Ok(json)
  }
}
