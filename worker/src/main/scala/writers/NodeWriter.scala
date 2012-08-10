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
    
    SQL("""
        INSERT INTO node (id, description, document_set_id) VALUES
          (nextval('node_seq'), {description}, {documentSetId})
        """).on("documentSetId" -> documentSetId, "description" -> root.description).
             executeInsert()
  }

}