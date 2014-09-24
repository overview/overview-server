package org.overviewproject.models.tables

import org.overviewproject.database.Slick.simple._
import org.overviewproject.tree.orm.NodeDocument // should be models.NodeDocument

class NodeDocumentsImpl(tag: Tag) extends Table[NodeDocument](tag, "node_document") {
  def nodeId = column[Long]("node_id")
  def documentId = column[Long]("document_id")
  def pk = primaryKey("node_document_pkey", (nodeId, documentId))

  def * = (nodeId, documentId) <> (NodeDocument.tupled, NodeDocument.unapply)
}

object NodeDocuments extends TableQuery(new NodeDocumentsImpl(_))
