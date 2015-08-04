package com.overviewdocs.models.tables

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.DocumentSetCreationJobNode

class DocumentSetCreationJobNodesImpl(tag: Tag) extends Table[DocumentSetCreationJobNode](tag, "document_set_creation_job_node") {
  def documentSetCreationJobId = column[Long]("document_set_creation_job_id")
  def nodeId = column[Long]("node_id")

  def * = (documentSetCreationJobId, nodeId) <> ((DocumentSetCreationJobNode.apply _).tupled, DocumentSetCreationJobNode.unapply)
}

object DocumentSetCreationJobNodes extends TableQuery(new DocumentSetCreationJobNodesImpl(_))
