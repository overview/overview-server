package models

import anorm._
import anorm.SqlParser._
import java.sql.Connection

import org.squeryl.Session

/**
 * Utility class form SubTreeLoader that performs database queries and returns results as 
 * a list of tuples.
 */
class SubTreeDataLoader {

  type NodeData = (Long, Long, String)
  type NodeDocument = (Long, Long, Long)
  
  private val IdColumn = "id"
  private val ChildIdColumn = "child_id"
  private val DescriptionColumn = "child_description"
    
  /**
   * @return a list of tuples: (parentId, childId, childDescription) for each node found in
   * a breadth first traversal of the tree to the specified depth, starting at the specified 
   * root.
   * @throws IllegalArgumentException if depth < 1 
   */
  def loadNodeData(rootId: Long, depth: Int)(implicit connection: Connection) : List[NodeData] = {
    require(depth > 0)
    
    val rootNode = SQL(RootNodeQuery).on("id" -> rootId).
      as(long(IdColumn) ~ str(DescriptionColumn) map(flatten) *)
    
    val rootAsChild = rootNode.map(n => (-1l, n._1, n._2))
    val childNodes = loadChildNodes(List(rootId), depth)
    
    rootAsChild ++ childNodes
  }
  
  
  def loadDocumentIds(nodeIds : List[Long])(implicit connection: Connection) : List[NodeDocument] = {
    val documentIds = SQL(
      """
        SELECT node_id, document_count, document_id
    	FROM (
    	  SELECT 
    		nd.node_id,
    		COUNT(nd.document_id) OVER (PARTITION BY nd.node_id) AS document_count,
    		nd.document_id,
    		RANK() OVER (PARTITION BY nd.node_id ORDER BY nd.document_id) AS pos
    	  FROM node_document nd
    	  WHERE nd.node_id IN ( """ + nodeIds.mkString(", ") + """)
    	  ORDER BY nd.document_id
    	) ss
    	WHERE ss.pos < 11
      """).as(long("node_id") ~ long("document_count") ~ long("document_id") map(flatten) *)

    documentIds
  } 
  
  private def loadChildNodes(nodes: List[Long], depth: Int)
                            (implicit connection: Connection) : List[NodeData] = {
    if (depth == 0 || nodes.size == 0) Nil
    else {
      val childNodes = SQL(childNodeQuery(nodes)).
    	as(long(IdColumn) ~ long(ChildIdColumn) ~ str(DescriptionColumn) map(flatten) *)
      
      val childNodeIds = childNodes.map(_._2)
      
      childNodes ++ loadChildNodes(childNodeIds, depth - 1)
    }
  }

  private val RootNodeQuery = 
    "SELECT node.id, node.description AS " + DescriptionColumn + " FROM node WHERE id = {id}"
        
  private def childNodeQuery(nodeIds: List[Long]) : String = {
    "SELECT node.parent_id AS " + IdColumn +
    ", node.id AS " + ChildIdColumn +
    ", node.description AS " + DescriptionColumn +
    "  FROM node WHERE parent_id IN (" + nodeIds.mkString(", ") + 
    ")"
  }

}