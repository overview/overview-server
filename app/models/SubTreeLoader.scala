package models

import scala.collection.JavaConversions._

import anorm._
import anorm.SqlParser._

import play.api.Play.current
import play.api.db.DB


class SubTreeLoader(rootId: Long, depth: Int, 
					loader: SubTreeDataLoader = new SubTreeDataLoader(),
					parser: SubTreeDataParser = new SubTreeDataParser()) {
  
  def loadNodes()(implicit connection : java.sql.Connection) : List[core.Node] = {
	
    val nodeData = loader.loadNodeData(rootId, depth)
    
    parser.createNodes(nodeData)
  }

  
  
}