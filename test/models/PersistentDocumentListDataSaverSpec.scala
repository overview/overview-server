package models

import anorm._
import anorm.SqlParser._
import helpers.DbSetup._
import helpers.DbTestContext
import java.sql.Connection
import org.specs2.mutable.Specification
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication

class PersistentDocumentListDataSaverSpec extends Specification {

  step(start(FakeApplication()))
  
  "PersistentDocumentListDataSaver" should {
    
    def insertTag(name: String, documentSetId: Long)(implicit c: Connection) : Long = {
      SQL("""
          INSERT INTO tag (id, name, document_set_id)
          VALUES (nextval('tag_seq'), {name}, {documentSetId})
          """).on("name" -> name, "documentSetId" -> documentSetId).
          executeInsert(scalar[Long] single)
    }
    
    def selectDocumentsWithTag(tagId: Long)(implicit c: Connection) : List[Long] = {
      SQL("""
          SELECT document_id FROM document_tag WHERE tag_id = {tagId}
          """).on("tagId" -> tagId).as(scalar[Long] *)
    }
    
    
    "add tag to selection, returning insert count" in new DbTestContext {
      val documentSetId = insertDocumentSet("PersistentDocumentListDataSaverSpec")
      
      val documentIds = for (i <- 1 to 5) yield 
        insertDocument(documentSetId, "title-" + i, "textUrl-" + i, "viewUrl-" + i)
      val nodeIds = Nil
      
      val tagId = insertTag("tag", documentSetId)
      
      val dataSaver = new PersistentDocumentListDataSaver()
      val count = dataSaver.addTag(tagId, nodeIds, documentIds)
      
      count must be equalTo(documentIds.size)
      
      val taggedDocuments = selectDocumentsWithTag(tagId)
      
      taggedDocuments must haveTheSameElementsAs(documentIds)
    }
    
    "add tag to selection with both nodes and documents" in new DbTestContext {
      val documentSetId = insertDocumentSet("PersistentDocumentListDataSaverSpec")
      
      val nodeIds = insertNodes(documentSetId, 3)
      
      val documentIds = nodeIds.flatMap(n => 
    	for (i <- 1 to 2) yield
    	  insertDocumentWithNode(documentSetId, "title", "textUrl", "viewUrl", n)
      )
      
      val tagId = insertTag("tag", documentSetId)
      
      val dataSaver = new PersistentDocumentListDataSaver()
      val count = dataSaver.addTag(tagId, nodeIds.drop(1), documentIds.take(4))
      
      count must be equalTo(2)
      
      val taggedDocuments = selectDocumentsWithTag(tagId)
      
      taggedDocuments must haveTheSameElementsAs(documentIds.slice(2, 4))
      
    }
    
    "not add a tag for a document more than once" in new DbTestContext {
      val documentSetId = insertDocumentSet("PersistentDocumentListDataSaverSpec")
      
      val documentIds = for (i <- 1 to 5) yield 
        insertDocument(documentSetId, "title-" + i, "textUrl-" + i, "viewUrl-" + i)
      val nodeIds = Nil
      
      val tagId = insertTag("tag", documentSetId)
      
      val dataSaver = new PersistentDocumentListDataSaver()
      val count = dataSaver.addTag(tagId, nodeIds.take(3), documentIds)
      val actualInsertsCount = dataSaver.addTag(tagId, nodeIds.drop(2), documentIds)
      
      actualInsertsCount must be equalTo(2)
      
      val taggedDocuments = selectDocumentsWithTag(tagId)
      
      taggedDocuments must haveTheSameElementsAs(documentIds)
    }.pendingUntilFixed
  }
  
  step(stop)
}