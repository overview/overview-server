package org.overviewproject.tree.orm

import org.overviewproject.test.DbSpecification
import org.overviewproject.test.DbSetup._
import org.overviewproject.tree.orm.DocumentType._
import org.squeryl.PrimitiveTypeMode._

class DocumentSpec extends DbSpecification {
  step(setupDb)
  
  "Document" should {
    
    "read and write optional title" in new DbTestContext {
      val documentSetId = insertDocumentSet("DocumentSpec")
      val documentTitle = Some("title")
      
      val document = Document(DocumentCloudDocument, documentSetId, title = documentTitle, documentcloudId = Some("dcId"))
      Schema.documents.insert(document)
      
      val foundDocument = Schema.documents.lookup(document.id).get
      
      foundDocument.title must be equalTo documentTitle
    }
  }
  step(shutdownDb)
}