package models


import helpers.DbTestContext
import helpers.DbSetup._
import org.specs2.mutable.Specification
import play.api.test._
import play.api.test.Helpers._
import play.api.Play.{start, stop}


class SubTreeDataLoaderTagQuerySpec extends Specification {

  step(start(FakeApplication()))
  
  "SubTreeDataLoader" should {
    
    trait NodesSetup extends DbTestContext {
      lazy val documentSetId = insertDocumentSet("SubTreeDataLoaderTagQuerySpec")
      lazy val nodeIds = insertNodes(documentSetId, 3)
      lazy val documentIds = insertDocumentsForeachNode(documentSetId, nodeIds, 4)
      
      val subTreeDataLoader = new SubTreeDataLoader()
    }
    
    "return tag counts for specified nodes" in new NodesSetup {
      val untaggedDocumentIds = insertDocumentsForeachNode(documentSetId, nodeIds, 2)
      val tagId1 = insertTag(documentSetId, "tag1")
      val tagId2 = insertTag(documentSetId, "tag2")
      
      tagDocuments(tagId1, documentIds.take(6))
      tagDocuments(tagId2, documentIds.slice(4, 7))
      
      val nodeTagCounts = subTreeDataLoader.loadNodeTagCounts(nodeIds)
      
      val expectedCounts = Seq((nodeIds(0), tagId1, 4l),
                               (nodeIds(1), tagId1, 2l),
                               (nodeIds(1), tagId2, 3l))

      nodeTagCounts must haveTheSameElementsAs(expectedCounts)                               
    }
    
    "return tag counts for all nodes if none specified" in new NodesSetup {
      val tagId = insertTag(documentSetId, "tag")
      tagDocuments(tagId, documentIds)
      
      val nodeTagCounts = subTreeDataLoader.loadNodeTagCounts(Nil)
      val expectedCounts = nodeIds.map((_, tagId, 4l))
      
      nodeTagCounts must haveTheSameElementsAs(expectedCounts)
    }
    
    "return tag ids for specified document ids" in new NodesSetup {
      val tagId1 = insertTag(documentSetId, "tag1")
      val tagId2 = insertTag(documentSetId, "tag2")
      val tagId3 = insertTag(documentSetId, "tag3")
      tagDocuments(tagId1, documentIds.take(8))
      tagDocuments(tagId2, documentIds.take(4))
      
      val documentTagIds = subTreeDataLoader.loadDocumentTags(documentIds)
      val tag1Documents = documentIds.take(8).map((_, tagId1))
      val tag2Documents = documentIds.take(4).map((_, tagId2))
      val tag3Documents = Nil
      val expectedDocumentTags = tag1Documents ++ tag2Documents ++ tag3Documents
      
      documentTagIds must haveTheSameElementsAs(expectedDocumentTags)
    }
    
    "return tag ids for all documents if no document ids are specified" in new NodesSetup {
      val tagId = insertTag(documentSetId, "tag")
      tagDocuments(tagId, documentIds)
      
      val documentTagIds = subTreeDataLoader.loadDocumentTags(Nil)
      val expectedDocumentTags = documentIds.map((_, tagId))
      
      documentTagIds must haveTheSameElementsAs(expectedDocumentTags)
    }
    
    "return tag data for documentSet" in new NodesSetup {
      val tagId1 = insertTag(documentSetId, "tag1")
      val tagId2 = insertTag(documentSetId, "tag2")
      tagDocuments(tagId1, documentIds)
      
      val tagData = subTreeDataLoader.loadTags(documentSetId)
      val expectedTagData = 
        documentIds.sorted.take(10).map(d => (tagId1, "tag1", 12, Some(d))) :+
        (tagId2, "tag2", 0, None)
      
      tagData must haveTheSameElementsAs(expectedTagData)
    }
  }
  
  step(stop)
}