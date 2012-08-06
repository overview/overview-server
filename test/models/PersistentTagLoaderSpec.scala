package models

import anorm._
import anorm.SqlParser._
import helpers.DbSetup._
import helpers.DbTestContext
import java.sql.Connection
import org.specs2.mutable.Specification
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication


class PersistentTagLoaderSpec extends Specification {

  def tagDocuments(tagId: Long, documentIds: Seq[Long])(implicit c: Connection) : Long = {
    SQL("""
        INSERT INTO document_tag (document_id, tag_id)
        SELECT id, {tagId} FROM document
        WHERE id IN """ + documentIds.mkString("(", ",", ")")
        ).on("tagId" -> tagId).executeUpdate()
  }
  
  step(start(FakeApplication()))
  
  "PersistentTagLoader" should {
    
    "get tag id by name if it exists" in new DbTestContext {
      val tagName = "taggy"
        
      val documentSetId = insertDocumentSet("TagLoaderSpec")
      val tagId = insertTag(documentSetId, tagName)
      
      val tagLoader = new PersistentTagLoader()
      
      val foundTag = tagLoader.loadByName(tagName)
      
      foundTag must be equalTo(Some(tagId))
    }
    
    "get None if tag does not exist" in new DbTestContext {
      val tagName = "taggy"
        
      val tagLoader = new PersistentTagLoader()
      
      val missingTag = tagLoader.loadByName(tagName)
      
      missingTag must beNone
    }
    
    "count total number of documents tagged" in new DbTestContext {
      val tagName = "taggy"
        
      val documentSetId = insertDocumentSet("TagLoaderSpec")
      val documentIds = for (i <- 1 to 5) yield 
        insertDocument(documentSetId, "title", "textUrl", "viewUrl")
      
      val tagId = insertTag(documentSetId, tagName)
      
      val tagLoader = new PersistentTagLoader()
      
      val initialCount = tagLoader.countDocuments(tagId)
      
      initialCount must be equalTo(0)
      
      tagDocuments(tagId, documentIds.take(3))
      
      val count = tagLoader.countDocuments(tagId)
      count must be equalTo(3)
    }
    
    "count tagged documents per node" in new DbTestContext {
      val tagName = "taggy"
        
      val documentSetId = insertDocumentSet("TagLoaderSpec")
      val nodeIds = insertNodes(documentSetId, 4)
      val documentIds = insertDocumentsForeachNode(documentSetId, nodeIds, 4)
      
      val tagId = insertTag(documentSetId, tagName)
      val tagLoader = new PersistentTagLoader()

      val initialCounts = tagLoader.countsPerNode(nodeIds, tagId)
      
      initialCounts must be empty
      
      tagDocuments(tagId, documentIds.take(8))
      val counts = tagLoader.countsPerNode(nodeIds, tagId)
      val expectedCounts = nodeIds.take(2).map((_, 4l))
      
      counts must haveTheSameElementsAs(expectedCounts)
    }
  }
  
  step(stop)
}