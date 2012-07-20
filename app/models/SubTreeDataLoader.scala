package models

import anorm._
import anorm.SqlParser._
import java.sql.Connection

class SubTreeDataLoader {

  type NodeData = (Long, Long, String)
  
  def loadNodeData(rootId: Long, depth: Int)(implicit connection: Connection) : List[NodeData] = {
    val rootNode = SQL(
      """
        SELECT node.id, node.description FROM node WHERE id = {id}
      """
    ).on("id" -> rootId).
    as(long("id") ~ str("description") map(flatten) single)
    
    val rootAsChild = (-1l, rootNode._1, rootNode._2)
    
    val childNodes = SQL(
      """
   	    SELECT node.parent_id AS id, node.id AS child_id, node.description AS child_description
	  	  FROM node WHERE parent_id IN ({id})
      """
    ).on("id" -> rootId).
    as(long("id") ~ long("child_id") ~ str("child_description") map(flatten) *)
    
    rootAsChild :: childNodes
  }
}