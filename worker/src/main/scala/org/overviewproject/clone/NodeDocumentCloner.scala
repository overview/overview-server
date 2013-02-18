package org.overviewproject.clone

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.persistence.{ NodeDocumentBatchInserter, Schema }

object NodeDocumentCloner {

  def clone(documentMapping: Map[Long, Long], nodeMapping: Map[Long, Long]) {
    val batchInserter = new NodeDocumentBatchInserter(500)
    val sourceNodes = nodeMapping.keys

    val sourceNodeDocuments =
      from(Schema.nodeDocuments)(nd => where(nd.nodeId in sourceNodes) select nd)

    val cloneNodeDocuments = for {
      nd <- sourceNodeDocuments
      nodeId <- nodeMapping.get(nd.nodeId)
      documentId <- documentMapping.get(nd.documentId)
    } batchInserter.insert(nodeId, documentId)

    batchInserter.flush
  }
}