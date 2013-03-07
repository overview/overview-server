package org.overviewproject.clone

import java.sql.Connection
import anorm._
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.persistence.NodeDocumentBatchInserter
import org.overviewproject.persistence.orm.Schema
import org.overviewproject.database.Database

object NodeDocumentCloner {
  private val DocumentSetIdMask: Long = 0x00000000FFFFFFFFl

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

  def dbClone(sourceDocumentSetId: Long, cloneDocumentSetId: Long): Boolean = {
    implicit val c: Connection = Database.currentConnection

    SQL("""
        INSERT INTO node_document (node_id, document_id)
          SELECT
            ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & node_id),
            ({cloneDocumentSetId} << 32) | ({documentSetIdMask} & document_id)
          FROM node_document
          INNER JOIN document ON 
            (document.document_set_id = {sourceDocumentSetId} AND
             node_document.document_id = document.id)
        """).on("cloneDocumentSetId" -> cloneDocumentSetId,
      "sourceDocumentSetId" -> sourceDocumentSetId,
      "documentSetIdMask" -> DocumentSetIdMask).execute
  }
}