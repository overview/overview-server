package models

import org.specs2.mock._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class PersistentTagSpec extends Specification with Mockito {
  implicit val unusedConnection : java.sql.Connection = null
  
  "PersistentTag" should {
	
    trait MockComponents extends Scope {
      val loader = mock[PersistentTagLoader]
      val parser = mock[DocumentListParser]
      val saver = mock[PersistentTagSaver]
      val documentSetId = 4l
      val dummyTagId = 23l
      val name = "a tag"
      
    }
    
    
    "be created by findOrCreateByName factory method if not in database" in new MockComponents {
        
      loader loadByName(documentSetId, name) returns None
      saver save(documentSetId, name) returns Some(dummyTagId)
      
      val tag = PersistentTag.findOrCreateByName(name, documentSetId, loader, parser, saver)
      
      there was one(loader).loadByName(documentSetId, name)
      there was one(saver).save(documentSetId, name)
      
      tag.id must be equalTo(dummyTagId)
    } 
    
    "be loaded by findOrCreateByName factory method if in database" in new MockComponents {
      loader loadByName(documentSetId, name) returns Some(dummyTagId)
            
      val tag = PersistentTag.findOrCreateByName(name, documentSetId, loader, parser, saver)
      
      there was one(loader).loadByName(documentSetId, name)
      there was no(saver).save(documentSetId, name)
      
      tag.id must be equalTo(dummyTagId)
      
    }
    
    "be loaded by findByName if in database" in new MockComponents {
      loader loadByName(documentSetId, name) returns Some(dummyTagId)
      
      val tag = PersistentTag.findByName(name, documentSetId, loader, parser, saver)
      
      tag must beSome
      tag.get.id must be equalTo(dummyTagId)
    }
    
    "return None from findByName if tag is not in database" in new MockComponents {
      loader loadByName(documentSetId, name) returns None
      
      val tag = PersistentTag.findByName(name, documentSetId, loader, parser, saver)
      
      tag must beNone
    }
    
    "should ask loader for number of documents with tag" in new MockComponents {
      val dummyCount = 454
      
      loader loadByName(documentSetId, name) returns Some(dummyTagId)
      
      loader countDocuments(dummyTagId) returns dummyCount
      
      val tag = PersistentTag.findOrCreateByName(name, documentSetId, loader, parser, saver)
      val count = tag.count
      
      there was one(loader).countDocuments(dummyTagId)
      
      count must be equalTo(dummyCount)
    }
    
    "should ask loader for number of documents with tag per node" in new MockComponents {
      val dummyCounts = Seq((1l, 5l), (2l, 3345l))
      val nodeIds = Seq(1l, 2l)
      
      loader loadByName(documentSetId, name) returns Some(dummyTagId)
      loader countsPerNode(nodeIds, dummyTagId) returns dummyCounts
      
      val tag = PersistentTag.findOrCreateByName(name, documentSetId, loader, parser, saver)
      val counts = tag.countsPerNode(nodeIds)
      
      there was one(loader).countsPerNode(nodeIds, dummyTagId)
      
      counts must be equalTo(dummyCounts)
    }
    
    "ask loader and parser to create tag" in new MockComponents {
    	
      val tagData = Seq((dummyTagId, name, 0l, None))
      val dummyTag = core.Tag(dummyTagId, name, core.DocumentIdList(Nil, 0))
      
      loader loadByName(documentSetId, name) returns Some(dummyTagId)
      loader loadTag(dummyTagId) returns tagData
      parser createTags(tagData) returns Seq(dummyTag)
      
      val persistentTag = PersistentTag.findOrCreateByName(name, documentSetId, loader, parser, saver)
      
      val tag = persistentTag.loadTag
      
      there was one(loader).loadTag(dummyTagId)
      there was one(parser).createTags(tagData)
      
      tag must be equalTo(dummyTag)
    }
    
    "load documents referenced by tag" in new MockComponents {
      val tag = core.Tag(dummyTagId, name, core.DocumentIdList(Seq(1l, 2l), 3))
      val dummyDocumentData = List((1l, "title", "documentCloudId"),
	(2l, "title", "documentCloudId"))
      val documentIds = Seq(1l, 2l)
      val dummyDocumentTagData = List((1l,5l), (2l, 15l))
      val dummyDocuments = List(
          core.Document(1l, "document1", "documentCloudId", Seq(5l)),
          core.Document(2l, "document2", "documentCloudId", Seq(15l)))
      
      loader loadByName(documentSetId, name) returns Some(dummyTagId)
      loader loadDocuments(documentIds) returns dummyDocumentData 
      loader loadDocumentTags(documentIds) returns dummyDocumentTagData
      parser createDocuments(dummyDocumentData, dummyDocumentTagData) returns dummyDocuments
      
      val persistentTag = PersistentTag.findOrCreateByName(name, documentSetId, 
    		  											   loader, parser, saver)
    		  											   
      val documents = persistentTag.loadDocuments(tag)
      
      there was one(loader).loadDocuments(documentIds) 
      there was one(loader).loadDocumentTags(documentIds)
      there was one(parser).createDocuments(dummyDocumentData, dummyDocumentTagData)
      
      documents must be equalTo(dummyDocuments)
    }
  }
}

