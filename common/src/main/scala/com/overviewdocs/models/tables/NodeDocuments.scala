package com.overviewdocs.models.tables

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.NodeDocument

class NodeDocumentsImpl(tag: Tag) extends Table[NodeDocument](tag, "node_document") {
  def nodeId = column[Long]("node_id")
  def documentId = column[Long]("document_id")
  def pk = primaryKey("node_document_pkey", (nodeId, documentId))

  def * = (nodeId, documentId) <> (NodeDocument.tupled, NodeDocument.unapply)
}

object NodeDocuments extends TableQuery(new NodeDocumentsImpl(_))
