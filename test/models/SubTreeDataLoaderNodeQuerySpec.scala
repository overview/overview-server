package models

import anorm._
import anorm.SqlParser._
import helpers.DbTestContext
import java.sql.Connection
import org.specs2.mutable.Specification
import org.squeryl.Session

import play.api.test._
import play.api.test.Helpers._
import play.api.Play.{start, stop}

class SubTreeDataLoaderNodeQuerySpec extends Specification  {
  
  step(start(FakeApplication()))
  
  
  private val TreeDepth = 6
  
  trait TreeCreated extends DbTestContext {
    lazy val documentSetId = insertDocumentSet
    lazy val nodeIds = writeBinaryTreeInDb(documentSetId, TreeDepth)
    lazy val rootId = nodeIds(0)
    
    lazy val subTreeDataLoader = new SubTreeDataLoader()
  }

  def insertDocumentSet(implicit connection: Connection) : Long = {
    SQL("""
      INSERT INTO document_set (id, query)
      VALUES (nextval('document_set_seq'), 'SubTreeDataLoaderDocumentQuerySpec')
      """).executeInsert().getOrElse(throw new Exception("fail"))
  }

  def insertNode(documentSetId: Long, parentId: Option[Long], description: String)(implicit connection: Connection) : Long = {
    SQL("""
      INSERT INTO node (id, document_set_id, parent_id, description)
      VALUES (nextval('node_seq'), {document_set_id}, {parent_id}, {description})
      """).on(
        'document_set_id -> documentSetId,
        'parent_id -> parentId,
        'description -> description
      ).executeInsert().getOrElse(throw new Exception("fail"))
  }
  

  def writeBinaryTreeInDb(documentSetId: Long, depth : Int)(implicit c: Connection) : List[Long] = {
    val id = insertNode(documentSetId, None, "root")

    id :: writeSubTreeInDb(documentSetId, id, depth - 1) 
  }

  def writeSubTreeInDb(documentSetId: Long, root : Long, depth: Int)(implicit c : Connection) : List[Long] = depth match {
    case 0 => Nil
    case _ => {
      val childId1 = insertNode(documentSetId, Some(root), "childA-" + root)
      val childId2 = insertNode(documentSetId, Some(root), "childB-" + root)

      val childTree1 = writeSubTreeInDb(documentSetId, childId1, depth - 1)
      val childTree2 = writeSubTreeInDb(documentSetId, childId2, depth - 1)

      childId1 :: childTree1 ++ (childId2 :: childTree2)
    }
  }
    
  "SubTreeDatabaseLoader" should {
    
    "include root node with no parent" in new TreeCreated {
      val nodeData = subTreeDataLoader.loadNodeData(rootId, 5)
      
      nodeData must contain((-1l, Some(rootId), "root"))
    }
    
    "load subTree of depth 2" in new TreeCreated {
      val nodeData = subTreeDataLoader.loadNodeData(rootId, 2)
      nodeData must have size(7)
      
      val nonLeafNodes = nodeData.map(_._2.get).take(3)
      val parentNodes = nodeData.map(_._1).tail.distinct
      
      nonLeafNodes must haveTheSameElementsAs(parentNodes)
    }
    
    "load subTree of depth 5" in new TreeCreated {
      val nodeData = subTreeDataLoader.loadNodeData(rootId, 5)
      nodeData must have size(63)
      
      val nonLeafNodes = nodeData.map(_._2.get).take(31)
      val parentNodes = nodeData.map(_._1).tail.distinct
      
      nonLeafNodes must haveTheSameElementsAs(parentNodes)
    }
    
    "loads leaf nodes with No child nodes specified" in new TreeCreated { 
      val lowestNonLeafNode = nodeIds(TreeDepth - 2)
      val nodeData = subTreeDataLoader.loadNodeData(lowestNonLeafNode, TreeDepth)

      nodeData must have size(5)
      
      val leafNodes = nodeData.drop(3)
      val leafNodeChildIds = leafNodes.map(_._2)
      leafNodeChildIds must be equalTo(List(None, None))
    }
    
    "handle incorrect depth parameter" in new TreeCreated {
      val nodeData = subTreeDataLoader.loadNodeData(123, 0) must
        throwAn[IllegalArgumentException] 
    }
      
    "handle missing rootid" in new TreeCreated {
      val nodeData = subTreeDataLoader.loadNodeData(-1, 4)
      
      nodeData must be equalTo(List((-1l, None, "")))
    }
    
    "return empty document list for nodes with no documents" in new TreeCreated {
      val nodes = nodeIds.take(5)
      
      val nodeDocuments = subTreeDataLoader.loadDocumentIds(nodes)
      
      nodeDocuments must be empty
    }
  }
  
  step(stop)
}
