package models

import scala.collection.JavaConversions._

import anorm._
import anorm.SqlParser._

import play.api.Play.current
import play.api.db.DB



class SubTreeLoader(rootId: Long) {
  
  def loadNodes() : List[core.Node] = {
    DB.withConnection { implicit connection => 
      val result : List[Long] = SQL(
          """
            SELECT nodes.node_id, ARRAY_AGG(nodes.child_id) AS child_ids
    		  FROM (
    		  	SELECT DISTINCT n6.parent_id AS node_id, n6.id AS child_id
    		  	FROM node n1
    		  	LEFT JOIN node n2 ON (n2.parent_id = n1.id)
    		  	LEFT JOIN node n3 ON (n3.id = n2.id OR n3.parent_id = n2.id)
    		  	LEFT JOIN node n4 ON (n4.id = n3.id OR n4.parent_id = n3.id)
    		  	LEFT JOIN node n5 ON (n5.id = n4.id OR n5.parent_id = n4.id)
    		  	LEFT JOIN node n6 ON (n6.id = n5.id OR n6.parent_id = n5.id)
    		  	WHERE n1.id = {root}
    		  	ORDER BY n6.parent_id, n6.id) nodes
    		  GROUP BY nodes.node_id
          """
        ).on("root" -> rootId).as(scalar[Long] *)
    		
    		
      result.map(core.Node(_))
    }
    
		  
  }

  
  
}