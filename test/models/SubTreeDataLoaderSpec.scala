package models

import anorm._
import helpers.DbTestContext
import java.sql.Connection
import org.specs2.mutable.Specification

class SubTreeDataLoaderSpec extends Specification {

  trait TreeCreated extends DbTestContext {
    lazy val nodeIds = writeBinaryTreeInDb(10)
    lazy val rootId = nodeIds(0)
    
    lazy val subTreeDataLoader = new SubTreeDataLoader()
  }
  
  def writeBinaryTreeInDb(depth : Int)(implicit connection: Connection) : List[Long] = {
    val id = SQL(
        """
          insert into node values (nextval('node_seq'), {description});
        """
    ).on("description" -> "root").executeInsert().getOrElse(-1l)
            
    id :: writeSubTreeInDb(id, depth - 1) 
  }

    
  def writeSubTreeInDb(root : Long, depth: Int)(implicit connection : Connection) : List[Long] = depth match {
    case 0 => Nil
    case _ => {
      val childId1 = SQL("insert into Node values (nextval('node_seq'), {description}, {parent})").
        on("description" -> ("childA-" + root), "parent" -> root).
        executeInsert().getOrElse(-1l)
      val childId2 = SQL("insert into Node values (nextval('node_seq'), {description}, {parent})").
        on("description" -> ("childB-" + root), "parent" -> root).
        executeInsert().getOrElse(-1l)
        
      val childTree1 = writeSubTreeInDb(childId1, depth - 1)
      val childTree2 = writeSubTreeInDb(childId2, depth - 1)
      
      childId1 :: childId2 ::
        childTree1.take(2) ++ childTree2.take(2) ++ 
        childTree1.drop(2) ++ childTree2.drop(2)
    }
  }
    
  "SubTreeDatabaseLoader" should {
   
    "include root node with children of requested subTree" in new TreeCreated {
      
      val childId1 = nodeIds(1)
      val childId2 = nodeIds(2)
      
      val nodeData = subTreeDataLoader.loadNodeData(rootId, 5)
      
      nodeData must contain((rootId, childId1, "childA-" + rootId), 
    		  			    (rootId, childId2, "childB-" + rootId))
      
    }
    
    "include root node with no parent" in new TreeCreated {
      
      val nodeData = subTreeDataLoader.loadNodeData(rootId, 5)
      
      nodeData must contain((-1l, rootId, "root"))
    }
    
    "load subTree of depth 4" in new TreeCreated {
      
      val nodeData = subTreeDataLoader.loadNodeData(rootId, 4)
      
      nodeData must have size(31)
    }
    
    "load subTree of depth 7" in new TreeCreated { skipped("not implemented") }
    "loads complete subtree if depth exceeds depth of tree" in new TreeCreated { skipped("not implemented") }
    "handles incorret depth parameter" in new TreeCreated { skipped("not implemented") }
    "handles missing rootid" in new TreeCreated { skipped("not implement") }
  }
}