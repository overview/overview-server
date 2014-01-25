package org.overviewproject.tree.orm

import org.overviewproject.test.DbSpecification
import org.overviewproject.test.IdGenerator._
import org.overviewproject.postgres.SquerylEntrypoint._

class DocumentSpec extends DbSpecification {
  step(setupDb)
  
  "Document" should {
    
    trait DocumentContext extends DbTestContext {
      var documentSet: DocumentSet = _ 
      var document: Document = _
      
      override def setupWithDb = {
        documentSet = Schema.documentSets.insert(DocumentSet(title = "DocumentSpec"))
        document = createDocument
      }
      
      def createDocument: Document = Document(documentSet.id , documentcloudId = Some("dcId"), id = nextDocumentId(documentSet.id ))
    } 
    
    trait DocumentWithDescription extends DocumentContext {
      val documentDescription = "description"
        
      override def createDocument: Document = Document(documentSet.id , documentDescription, documentcloudId = Some("dcId"), id = nextDocumentId(documentSet.id ))
    }
    
    trait DocumentWithTitle extends DocumentContext {
      val documentTitle = Some("title")
      
      override def createDocument: Document = Document(documentSet.id , title = documentTitle, documentcloudId = Some("dcId"), id = nextDocumentId(documentSet.id ))
    }
    
    "read and write description" in new DocumentWithDescription {
      Schema.documents.insert(document)
      
      val foundDocument = Schema.documents.lookup(document.id).get
      foundDocument.description must be equalTo documentDescription
    }
    
    "set default description to empty String" in new DocumentContext {
      Schema.documents.insert(document)
      
      val foundDocument = Schema.documents.lookup(document.id).get
      foundDocument.description must be equalTo ""
    }
    
    "read and write optional title" in new DocumentWithTitle {
      Schema.documents.insert(document)
      
      val foundDocument = Schema.documents.lookup(document.id).get
      
      foundDocument.title must be equalTo documentTitle
    }
    
    "set title to None by default" in new DocumentContext {
      document.title must beNone
      
      Schema.documents.insert(document)
      
      val foundDocument = Schema.documents.lookup(document.id).get
      
      foundDocument.title must beNone
    }
  }
  step(shutdownDb)
}