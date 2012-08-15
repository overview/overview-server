package controllers

import scala.collection.JavaConversions._
import scala.io
import scala.util.Random
import play.api.db.DB
import play.api.Play
import play.api.Play.current
import play.api.mvc._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json._
import models._
import anorm.SQL
import anorm.SqlParser.scalar


object TreeController extends Controller {
  def root(id: Long) = Action {
    // FIXME handle security

    DB.withTransaction { implicit connection =>
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
  }

  def node(id: Long, nodeId: Long) = Action {
    // FIXME handle security

    DB.withTransaction { implicit connection =>
      val subTreeLoader = new SubTreeLoader(nodeId, 4)
      val nodes = subTreeLoader.loadNodes
      val documents = subTreeLoader.loadDocuments(nodes)
      val tags = subTreeLoader.loadTags(id)
      
      val json = views.json.Tree.show(nodes, documents, tags)
      Ok(json)
    }
  }
}
