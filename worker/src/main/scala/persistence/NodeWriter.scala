/*
 * NodeWriter.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package persistence

import anorm._
import org.overviewproject.clustering.DocTreeNode
import java.sql.Connection

/**
 * Writes out tree with the given root node to the database.
 * Inserts entries into document and node_document tables. Documents contained by
 * the nodes must already exist in the database.
 */
class NodeWriter(documentSetId: Long) {
  val batchInserter = new NodeDocumentBatchInserter(500)

  def write(root: DocTreeNode)(implicit c: Connection) {
    writeSubTree(root, None)
    batchInserter.flush
  }

  private def writeSubTree(node: DocTreeNode, parentId: Option[Long])(implicit c: Connection) {
    val nodeId = SQL("""
        INSERT INTO node (description, parent_id, document_set_id) VALUES
          ({description}, {parentId}, {documentSetId})
        """).on("documentSetId" -> documentSetId,
                "description" -> node.description,
                "parentId" -> parentId).
      executeInsert().get

    node.docs.foreach(batchInserter.insert(nodeId, _))

    node.children.foreach(writeSubTree(_, Some(nodeId)))
  }

}
