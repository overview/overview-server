package models

import anorm._
import anorm.SqlParser._
import java.sql.Connection

import org.squeryl.Session

import DatabaseStructure._ 

/**
 * Utility class form SubTreeLoader that performs database queries and returns results as 
 * a list of tuples.
 */
class SubTreeDataLoader {

  /**
   * @return a list of tuples: (parentId, Some(childId), childDescription) for each node found in
   * a breadth first traversal of the tree to the specified depth, starting at the specified 
   * root. Nodes with no children will return (id, None, "")
   * @throws IllegalArgumentException if depth < 1 
   */
  def loadNodeData(rootId: Long, depth: Int)(implicit connection: Connection) : List[NodeData] = {
    require(depth > 0)
    
    val rootNode = SQL(RootNodeQuery).on("id" -> rootId).
      as(long(IdColumn) ~ str(DescriptionColumn) map(flatten) *)
    
    val rootAsChild = rootNode.map(n => (NoId, Some(n._1), n._2))
    val childNodes = loadChildNodes(List(rootId), depth)
     
    rootAsChild ++ childNodes
  }
  
  /**
   * @return a list of tuples:(nodeId, totalDocumentCount, documentId) for each nodeId. A maximum of 
   * 10 documentIds are returned for each documentId. totalDocumentCount is the total number of documents
   * associated with the nodeId in the database
   */
  def loadDocumentIds(nodeIds : List[Long])(implicit connection: Connection) : List[NodeDocument] = {
    SQL(nodeDocumentQuery(nodeIds)).
    	as(DocumentIdParser map(flatten) *)
  } 
  
  /** 
   * @ return a list of tuples: (documentId, title, textUrl, viewUrl) for each documentId.
   */
  def loadDocuments(documentIds: List[Long])(implicit connection: Connection) : List[DocumentData] = {
    SQL(documentQuery(documentIds)).
        as(DocumentParser map(flatten) *)
  }
  
  private def loadChildNodes(nodes: List[Long], depth: Int)
                            (implicit connection: Connection) : List[NodeData] = {
    if (depth == 0 || nodes.size == 0) Nil
    else {
      val childNodeData = SQL(childNodeQuery(nodes)).as(NodeParser map(flatten) *)
      
      val leafNodeData = dataForLeafNodes(nodes, childNodeData)
      
      val childNodeIds = childNodeData.map(_._2.get)
      
      childNodeData ++ leafNodeData ++ loadChildNodes(childNodeIds, depth - 1)
    }
  }
  
  private def dataForLeafNodes(nodes: List[Long], childNodeData: List[NodeData]) : List[NodeData] = {
    val nodesWithChildNodes = childNodeData.map(_._1)
    val leafNodes = nodes.diff(nodesWithChildNodes)
    
    leafNodes.map((_, None, ""))
  }

  private val RootNodeQuery = 
    "SELECT node.id, node.description AS " + DescriptionColumn + " FROM node WHERE id = {id}"
        
  private def childNodeQuery(nodeIds: List[Long]) : String = {
    "SELECT node.parent_id AS " + IdColumn +
    ", node.id AS " + ChildIdColumn +
    ", node.description AS " + DescriptionColumn +
    "  FROM node WHERE parent_id IN " + idList(nodeIds)
  }
  
  private def nodeDocumentQuery(nodeIds: List[Long]) : String = {
    """
      SELECT node_id AS id, document_count, document_id
	  FROM (
    	SELECT 
    	  nd.node_id,
    	  COUNT(nd.document_id) OVER (PARTITION BY nd.node_id) AS document_count,
    	  nd.document_id,
    	  RANK() OVER (PARTITION BY nd.node_id ORDER BY nd.document_id) AS pos
    	FROM node_document nd
    	WHERE nd.node_id IN """ + idList(nodeIds) + 
    """
    	ORDER BY nd.document_id
      ) ss
      WHERE ss.pos < 11
    """    
  }
  
  private def documentQuery(documentIds: List[Long]) : String = {
	"""
      SELECT id, title, text_url, view_url
      FROM document
      WHERE id IN """ + idList(documentIds)     
  }
  private def idList(ids: List[Long]) : String = "(" + ids.mkString(", ") + ")"

}