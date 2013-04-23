package org.overviewproject.documentcloud

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class RetrievableDocumentSpec extends Specification {

  "RetrievableDocument" should {
    
    trait DocumentContext extends Scope {
      val id = "documentCloudId"
      val title = "title"
      val pageUrlPrefix = "pageUrlTemplate-p"
      val documentInfo = Document(id, title, "access", s"$pageUrlPrefix{page}")
    }
    
    trait CompleteDocumentContext extends DocumentContext {
      val retrievableDocument: RetrievableDocument = CompleteDocument(documentInfo)
    }
    
    trait PageDocumentContext extends DocumentContext {
      val pageNum = 5
      val pageTitle = s"$title p.$pageNum" 
      val retrievableDocument: RetrievableDocument = DocumentPage(documentInfo, pageNum)
      
      
    }
    "return the document text url for CompleteDocuments" in new CompleteDocumentContext {
      retrievableDocument.url must be equalTo(s"https://www.documentcloud.org/api/documents/$id.txt")
    }
    
    "return the original document id and title for CompleteDocument" in new CompleteDocumentContext {
      retrievableDocument.id must be equalTo(id)
      retrievableDocument.title must be equalTo(title)
    }
    
    "return the document page url for DocumentPage" in new PageDocumentContext {
      retrievableDocument.url must be equalTo(s"$pageUrlPrefix$pageNum")
    }
    
    "return id with page number" in new PageDocumentContext {
      retrievableDocument.id must be equalTo(s"$id#p$pageNum")
    }
    
    "return title with page number" in new PageDocumentContext {
      retrievableDocument.title must be equalTo(pageTitle)
    }
    
    
  }
}