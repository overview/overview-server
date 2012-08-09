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
    
    trait TagCreated extends DbTestContext {
      
      lazy val documentSetId = insertDocumentSet("PersistentDocumentListDataSaverSpec")
      lazy val tagId = insertTag(documentSetId, "tag")
      lazy val dataSaver = new PersistentDocumentListDataSaver()
      

      def selectDocumentsWithTag(tagId: Long): List[Long] = {
        SQL("""
          SELECT document_id FROM document_tag WHERE tag_id = {tagId}
          """).on("tagId" -> tagId).as(scalar[Long] *)
      }

    }
    
    "add tag to selection, returning insert count" in new TagCreated {
      val nodeIds = insertNodes(documentSetId, 1)
      val documentIds = insertDocumentsForeachNode(documentSetId, nodeIds, 5)
      
      val count = dataSaver.addTag(tagId, Nil, Nil, documentIds)
      
      count must be equalTo(documentIds.size)
      
      val taggedDocuments = selectDocumentsWithTag(tagId)
      
      taggedDocuments must haveTheSameElementsAs(documentIds)
    }
    
    "add tag to selection with nodes, tags, and documents" in new TagCreated {
      val nodeIds = insertNodes(documentSetId, 3)
      val documentIds = insertDocumentsForeachNode(documentSetId, nodeIds, 2)
      val tagId1 = insertTag(documentSetId, "tag1")
      
      tagDocuments(tagId1, documentIds.take(5))
      val tagIds = Seq(tagId1)
      
      val count = dataSaver.addTag(tagId, nodeIds.drop(1), tagIds, documentIds.take(6))
      
      count must be equalTo(3)
      
      val taggedDocuments = selectDocumentsWithTag(tagId)
      
      taggedDocuments must haveTheSameElementsAs(documentIds.slice(2, 5))
      
    }
    
    "not add a tag for a document more than once" in new TagCreated {
      val nodeIds = insertNodes(documentSetId, 4)
      val documentIds = insertDocumentsForeachNode(documentSetId, nodeIds, 2)
      val tagIds = Nil
      
      val count = dataSaver.addTag(tagId, nodeIds.take(1), tagIds, documentIds)
      val actualInsertsCount = dataSaver.addTag(tagId, nodeIds, tagIds, documentIds)
      
      actualInsertsCount must be equalTo(6)
      
      val taggedDocuments = selectDocumentsWithTag(tagId)
      
      taggedDocuments must haveTheSameElementsAs(documentIds)
    }
    
    "tag all documents if selection is empty" in new TagCreated {
      val nodeIds = insertNodes(documentSetId, 1)
      val documentIds = insertDocumentsForeachNode(documentSetId, nodeIds, 10)
      
      val count = dataSaver.addTag(tagId, Nil, Nil, Nil)
      
      count must be equalTo(10)
    }
    
    "remove tag from selection" in new TagCreated {
      val nodeIds = insertNodes(documentSetId, 1)
      val documentIds = insertDocumentsForeachNode(documentSetId, nodeIds, 10)
      val tagIds = Nil
      
      dataSaver.addTag(tagId, nodeIds, tagIds, documentIds)
      
      val removedCount = dataSaver.removeTag(tagId, nodeIds, tagIds, documentIds.take(5))
      
      removedCount must be equalTo(5)
      
      val taggedDocuments = selectDocumentsWithTag(tagId)
      taggedDocuments must haveTheSameElementsAs(documentIds.drop(5))
    }
    
    "remove tags from all documents on empty selection" in new TagCreated {
      val nodeIds = insertNodes(documentSetId, 1)
      val documentIds = insertDocumentsForeachNode(documentSetId, nodeIds, 10)
      
      dataSaver.addTag(tagId, nodeIds, Nil, documentIds)
      
      val removedCount = dataSaver.removeTag(tagId, Nil, Nil, Nil)
      
      removedCount must be equalTo(10)
      
      val taggedDocuments = selectDocumentsWithTag(tagId)
      taggedDocuments must be empty
    }
    
    "remove tag not in selection returns 0 count" in new TagCreated {
      val nodeIds = insertNodes(documentSetId, 1)
      val documentIds = insertDocumentsForeachNode(documentSetId, nodeIds, 10)
      val tagIds = Nil
      
      val noChange = dataSaver.removeTag(tagId, nodeIds, Nil, documentIds)
      
      noChange must be equalTo(0)
    }
  }
  
  step(stop)
}