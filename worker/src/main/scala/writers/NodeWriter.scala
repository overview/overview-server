/*
 * NodeWriter.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package writers

import anorm._
import clustering.DocTreeNode
import java.sql.Connection

/**
 * Writes out tree with the given root node to the database.
 * Inserts entries into document and node_document tables. Documents contained by
 * the nodes must already exist in the database.
 */
class NodeWriter(documentSetId: Long) {
  
  def write(root: DocTreeNode)(implicit c: Connection) {
    
    val rootId = SQL("""
        INSERT INTO node (id, description, document_set_id) VALUES
          (nextval('node_seq'), {description}, {documentSetId})
        """).on("documentSetId" -> documentSetId, "description" -> root.description).
             executeInsert().get
             
    root.children.foreach(writeSubTree(_, rootId))
  }
  
  private def writeSubTree(node: DocTreeNode, parentId: Long)(implicit c: Connection) {
    val nodeId = SQL("""
        INSERT INTO node (id, description, parent_id, document_set_id) VALUES
          (nextval('node_seq'), {description}, {parentId}, {documentSetId})
        """).on("documentSetId" -> documentSetId, 
                "description" -> node.description,
                "parentId" -> parentId).
             executeInsert().get

     node.children.foreach(writeSubTree(_, nodeId))
  }

}