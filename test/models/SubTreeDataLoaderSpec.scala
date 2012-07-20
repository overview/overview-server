package models

import anorm._

import helpers.DbTestContext
import java.sql.Connection
import Math.pow
import org.specs2.mutable.Specification

class SubTreeDataLoaderSpec extends Specification {
  
  private val TreeDepth = 6
  
  trait TreeCreated extends DbTestContext {
    lazy val nodeIds = writeBinaryTreeInDb(TreeDepth)
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
      
      childId1 :: childTree1 ++ (childId2 :: childTree2)
    }
  }
    
  "SubTreeDatabaseLoader" should {
    
    "include root node with no parent" in new TreeCreated {
      val nodeData = subTreeDataLoader.loadNodeData(rootId, 5)
      
      nodeData must contain((-1l, rootId, "root"))
    }
    
    "load subTree of depth 2" in new TreeCreated {
      val nodeData = subTreeDataLoader.loadNodeData(rootId, 2)
      nodeData must have size(7)
      
      val nonLeafNodes = nodeData.map(_._2).take(3)
      val parentNodes = nodeData.map(_._1).tail.distinct
      
      nonLeafNodes must haveTheSameElementsAs(parentNodes)
    }
    
    "load subTree of depth 5" in new TreeCreated {
      val nodeData = subTreeDataLoader.loadNodeData(rootId, 5)
      nodeData must have size(63)
      
      val nonLeafNodes = nodeData.map(_._2).take(31)
      val parentNodes = nodeData.map(_._1).tail.distinct
      
      nonLeafNodes must haveTheSameElementsAs(parentNodes)
    }
    
    "loads complete subtree if depth exceeds depth of tree" in new TreeCreated { 
      val lowestNonLeafNode = nodeIds(TreeDepth - 2)
      val nodeData = subTreeDataLoader.loadNodeData(lowestNonLeafNode, TreeDepth / 2)
      nodeData must have size(3)
    }
    
    "handles incorret depth parameter" in new TreeCreated { 
      val nodeData = subTreeDataLoader.loadNodeData(rootId, 0) must
        throwAn[IllegalArgumentException] 
    }
    
    "handles missing rootid" in new TreeCreated { 
      val nodeData = subTreeDataLoader.loadNodeData(-123, 4)
      
      nodeData must be empty
    }
  }
}