package models

import scala.collection.JavaConversions._

import anorm._
import anorm.SqlParser._

import play.api.Play.current
import play.api.db.DB

/**
 * Loads data from the database about subTrees
 */
class SubTreeLoader(rootId: Long, depth: Int, 
					loader: SubTreeDataLoader = new SubTreeDataLoader(),
					parser: SubTreeDataParser = new SubTreeDataParser()) {
  
  /**
   * @return a list of all the Nodes in the subTree
   */
  def loadNodes()(implicit connection : java.sql.Connection) : List[core.Node] = {
	
    val nodeData = loader.loadNodeData(rootId, depth)
    
    parser.createNodes(nodeData, Nil)
  }

  def loadDocuments(nodes: List[core.Node])(implicit connection : java.sql.Connection) : List[core.Document] = {
    val nodeIds = nodes.map(_.id)
    val documentData = loader.loadDocuments(nodeIds)
    
    parser.createDocuments(documentData.distinct)
  }
  
}