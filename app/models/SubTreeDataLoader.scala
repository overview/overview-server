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
class SubTreeDataLoader extends DocumentTagDataLoader {
      
  /**
   * @return a list of tuples: (parentId, Some(childId), childDescription) for each node found in
   * a breadth first traversal of the tree to the specified depth, starting at the specified 
   * root. Nodes with no children will return (id, None, "")
   * @throws IllegalArgumentException if depth < 1 
   */
  def loadNodeData(rootId: Long, depth: Int)(implicit connection: Connection) : List[NodeData] = {
    require(depth > 0)
    
    val rootNode = rootNodeQuery(rootId)
    
    val rootAsChild = rootNode.map(n => (NoId, Some(n._1), n._2))
    val childNodes = loadChildNodes(List(rootId), depth)
     
    rootAsChild ++ childNodes
  }
  
  /**
   * @return a list of tuples:(nodeId, totalDocumentCount, documentId) for each nodeId. A maximum of 
   * 10 documentIds are returned for each documentId. totalDocumentCount is the total number of documents
   * associated with the nodeId in the database
   */
  def loadDocumentIds(nodeIds : Seq[Long])(implicit connection: Connection) : List[NodeDocument] = {
    nodeDocumentQuery(nodeIds)
  } 
  
  /** 
   * @ return a list of tuples: (documentId, title, textUrl, viewUrl) for each documentId.
   */
  def loadDocuments(documentIds: Seq[Long])(implicit connection: Connection) : List[DocumentData] = {
    documentQuery(documentIds)
  }
  
  def loadNodeTagCounts(nodeIds: Seq[Long])(implicit c: Connection) : List[NodeTagCountData] = {
    nodeTagCountQuery(nodeIds)
  }
  
  def loadTags(documentSetId: Long)(implicit c: Connection) : List[TagData] = {
    tagQuery(documentSetId)
  }
  
  private def loadChildNodes(nodes: Seq[Long], depth: Int)
                            (implicit connection: Connection) : List[NodeData] = {
    if (depth == 0 || nodes.size == 0) Nil
    else {
      val childNodeData = childNodeQuery(nodes)
      
      val leafNodeData = dataForLeafNodes(nodes, childNodeData)
      
      val childNodeIds = childNodeData.map(_._2.get)
      
      childNodeData ++ leafNodeData ++ loadChildNodes(childNodeIds, depth - 1)
    }
  }
  
  private def dataForLeafNodes(nodes: Seq[Long], childNodeData: List[NodeData]) : Seq[NodeData] = {
    val nodesWithChildNodes = childNodeData.map(_._1)
    val leafNodes = nodes.diff(nodesWithChildNodes)
    
    leafNodes.map((_, None, ""))
  }

  private def rootNodeQuery(rootNodeId: Long)(implicit c: Connection) : List[(Long, String)]= {
    val descriptionParser = long("id") ~ str("description")
    
    SQL("SELECT node.id, node.description FROM node WHERE id = {rootId}").
      on("rootId" -> rootNodeId).
      as(descriptionParser map(flatten) *)
  }
        
  private def childNodeQuery(nodeIds: Seq[Long])(implicit c: Connection) : List[NodeData] = {
    val nodeParser = long("parent_id") ~ get[Option[Long]]("id") ~ str("description")

    SQL(
      """
        SELECT node.parent_id, node.id, node.description
        FROM node WHERE parent_id IN 
      """ + idList(nodeIds)
    ).as(nodeParser map(flatten) *)
  }
  
  private def nodeDocumentQuery(nodeIds: Seq[Long])(implicit c: Connection) : List[NodeDocument] = {
    val documentIdParser = long("node_id") ~ long("document_count") ~ long("document_id")

    SQL(
      """
        SELECT node_id, document_count, document_id
	    FROM (
    	  SELECT 
    	    nd.node_id,
    	    COUNT(nd.document_id) OVER (PARTITION BY nd.node_id) AS document_count,
    	    nd.document_id,
    	    RANK() OVER (PARTITION BY nd.node_id ORDER BY nd.document_id) AS pos
    	  FROM node_document nd
    	  WHERE nd.node_id IN 
      """ + idList(nodeIds) + 
      """
    	  ORDER BY nd.document_id
        ) ss
        WHERE ss.pos < 11
      """
    ).
    as(documentIdParser map(flatten) *)
  }
  
  private def documentQuery(documentIds: Seq[Long])(implicit c: Connection) : List[DocumentData]= {
    val documentParser = long("id") ~ str("title") ~ str("text_url") ~ str("view_url")

    SQL(
      """
        SELECT id, title, text_url, view_url
        FROM document
        WHERE id IN 
      """ + idList(documentIds)
    ).
    as(documentParser map(flatten) *)
  }
  
  private def nodeTagCountQuery(nodeIds: Seq[Long])(implicit c: Connection) : List[NodeTagCountData] = {
    val whereNodeIsSelected = nodeIds match {
      case Nil => ""
      case _ => "WHERE node_document.node_id IN " + idList(nodeIds) 
    }
    
	val nodeTagCountParser = long("node_id") ~ long("tag_id") ~ long("count")
    
    SQL("""
        SELECT node_document.node_id, document_tag.tag_id, COUNT(document_tag.tag_id)
        FROM node_document 
        INNER JOIN document_tag ON node_document.document_id = document_tag.document_id """ +
        whereNodeIsSelected + 
        """
        GROUP BY node_document.node_id, document_tag.tag_id
    	""").as(nodeTagCountParser map(flatten) *)    
  }
  
  private def tagQuery(documentSetId: Long)(implicit c: Connection) : List[TagData] = {
    val tagDataParser = long("tag_id") ~ str("tag_name") ~ long("document_count") ~ 
    				    get[Option[Long]]("document_id")
    SQL("""
    	SELECT tag_id, tag_name, document_count, document_id
    	FROM (
    	  SELECT t.id AS tag_id, t.name AS tag_name,
            COUNT(dt.document_id) OVER (PARTITION BY dt.tag_id) AS document_count,
    		dt.document_id,
            RANK() OVER (PARTITION BY dt.tag_id ORDER BY dt.document_id) AS pos
    	  FROM tag t
    	  LEFT JOIN document_tag dt ON t.id = dt.tag_id
          WHERE t.document_set_id = {documentSetId}
    	  ORDER BY t.name, dt.document_id
    	) ss
    	WHERE ss.pos < 11        
        """).on("documentSetId" -> documentSetId).
        as(tagDataParser map(flatten) *)
  }
}