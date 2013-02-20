package org.overviewproject.persistence

import anorm._
import anorm.SqlParser
import org.overviewproject.test.DbSpecification
import org.overviewproject.test.DbSetup._

class DocumentSetLoaderSpec extends DbSpecification {
  step(setupDb)

  "DocumentSetLoader" should {

    "load query and title from id" in new DbTestContext {
      val query = "DocumentSetLoaderSpec"
      val title = "From query: " + query

      val documentSetId = insertDocumentSet(query)

      val documentSet = DocumentSetLoader.load(documentSetId)
      documentSet must beSome.like {
        case d =>
          d.documentSetType must be equalTo("DocumentCloudDocumentSet")
          d.title must be equalTo (title)
          d.query must beSome(query)
      }
    }
    
    "load title and uploadedFileId for CsvImportDocumentSets" in new DbTestContext {
      val uploadedFileId = insertUploadedFile("contentDisposition", "contentType", 100)
      val documentSetId = insertCsvImportDocumentSet(uploadedFileId)
      
      val documentSet = DocumentSetLoader.load(documentSetId)
      documentSet must beSome.like { 
        case d =>
          d.documentSetType must be equalTo("CsvImportDocumentSet")
          d.uploadedFileId must beSome(uploadedFileId)
      }
    } 
  }

  step(shutdownDb)
}
