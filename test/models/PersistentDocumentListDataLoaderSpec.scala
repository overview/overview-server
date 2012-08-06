package models

import anorm._
import anorm.SqlParser._
import helpers.DbSetup._
import helpers.DbTestContext
import java.sql.Connection
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }


class PersistentDocumentListDataLoaderSpec extends Specification {

  step(start(FakeApplication()))

  "PersistentDocumentListDataLoader" should {
    
    trait NodesAndDocuments extends DbTestContext {
      lazy val dataLoader = new PersistentDocumentListDataLoader()
      lazy val documentSet = insertDocumentSet("PersistentDocumentListDataLoaderSpec")
      lazy val nodeIds = insertNodes(documentSet, 3) // must access nodeIds in tests to insert them in Databas

      lazy val documentIds = nodeIds.flatMap { n =>
        for (_ <- 1 to 2) yield insertDocumentWithNode(documentSet,
        											   "title", "textUrl", "viewUrl", n)
      }      
    }
    
    "load document data for specified nodes with no other constraints" in new NodesAndDocuments {
      val selectedNodes = nodeIds.take(2)
      val expectedDocumentIds = documentIds.take(4)
      
      val documentData = 
        dataLoader.loadSelectedDocumentSlice(selectedNodes, Nil, 0, 6)
        
      val loadedIds = documentData.map(_._1)
      
      loadedIds must haveTheSameElementsAs(expectedDocumentIds)
      documentData must have(_._2 == "title")
      documentData must have(_._3 == "textUrl")
      documentData must have(_._4 == "viewUrl")
    }

    "load document data for specified document ids with no other constraints" in new NodesAndDocuments {
      val selectedDocuments = documentIds.take(3)
      
      val persistentDocumentListDataLoader =
        new PersistentDocumentListDataLoader()

      val documentData = 
        persistentDocumentListDataLoader.loadSelectedDocumentSlice(Nil, selectedDocuments,
        														   0, 6)
      val loadedIds = documentData.map(_._1)
      
      loadedIds must haveTheSameElementsAs(documentIds.take(3))
    }
    
    "load intersection of documents specified by nodes and document ids" in new NodesAndDocuments {
      val selectedNodes = nodeIds.take(2)
      val selectedDocuments = documentIds.drop(1)
      
      val documentData = 
        dataLoader.loadSelectedDocumentSlice(selectedNodes, selectedDocuments, 0, 6)
        
      val loadedIds = documentData.map(_._1)
      
      loadedIds must haveTheSameElementsAs(documentIds.slice(1, 4))
    }
    

    "load slice of selected documents" in new NodesAndDocuments {
      val expectedDocumentIds = documentIds.slice(2, 5)

      val documentData = dataLoader.loadSelectedDocumentSlice(nodeIds, Nil, 2, 3)
      val loadedIds = documentData.map(_._1)
      
      loadedIds must haveTheSameElementsAs(expectedDocumentIds)     
    }
    
    "return nothing if slice offset is larger than total number of Rows" in new NodesAndDocuments {
      val selectedDocuments = documentIds
      
      val documentData = dataLoader.loadSelectedDocumentSlice(nodeIds, Nil, 10, 4)
      
      documentData must be empty
    }
    
    "return all documents if selection is empty" in new NodesAndDocuments {
      val selectedDocuments = documentIds
       
      val documentData = dataLoader.loadSelectedDocumentSlice(Nil, Nil, 0, 6)
        
      val loadedIds = documentData.map(_._1)
      
      loadedIds must haveTheSameElementsAs(documentIds)      
    }
    
    "return total number of results in selection" in new NodesAndDocuments {
      val selectedNodes = nodeIds.take(2)
      val selectedDocuments = documentIds
    
      val count = dataLoader.loadCount(selectedNodes, selectedDocuments)
      
      count must be equalTo(4)
    }
    
    "return 0 count if selection result is empty" in new NodesAndDocuments {
      val selectedNodes = nodeIds.take(1)
      val selectedDocuments = documentIds.drop(3)
      
      val count = dataLoader.loadCount(selectedNodes, selectedDocuments)
      
      count must be equalTo(0)
    }
    
  }

  step(stop)
}