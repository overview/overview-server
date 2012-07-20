package models

import anorm._
import anorm.SqlParser._
import java.sql.Connection

class SubTreeDataLoader {

  type NodeData = (Long, Long, String)
  
    
  def loadNodeData(rootId: Long, depth: Int)(implicit connection: Connection) : List[NodeData] = {
    require(depth > 0)
    
    val rootNode = SQL(RootNodeQuery).on("id" -> rootId).
      as(long("id") ~ str("description") map(flatten) *)
    
    val rootAsChild = rootNode.map(n => (-1l, n._1, n._2))
    
    val childNodes = loadChildNodes(List(rootId), depth)
    
    
    rootAsChild ++ childNodes
  }
  
  private def loadChildNodes(nodes: List[Long], depth: Int)
                            (implicit connection: Connection) : List[NodeData] = {
    if (depth == 0 || nodes.size == 0) Nil
    else {
      val childNodes = SQL(childNodeQuery(nodes)).
    	as(long("id") ~ long("child_id") ~ str("child_description") map(flatten) *)
      
      val childNodeIds = childNodes.map(_._2)
      
      childNodes ++ loadChildNodes(childNodeIds, depth - 1)
    }
  }

  private val RootNodeQuery = 
    """
      SELECT node.id, node.description FROM node WHERE id = {id}
    """
    
  private def childNodeQuery(nodeIds: List[Long]) : String = {
    """
      SELECT node.parent_id AS id, node.id AS child_id, node.description AS child_description
        FROM node WHERE parent_id IN (""" + nodeIds.mkString(", ") + """)
    """
  }

}