package persistence

import anorm._
import anorm.SqlParser
import org.overviewproject.test.DbSpecification
import testutil.DbSetup._

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
          d.query must beSome.like { case q => q must be equalTo (query) }
      }
    }
    
    "load title and uploadedFileId for CsvImportDocumentSets" in new DbTestContext {
      val uploadedFileId = insertUploadedFile(0, "contentDisposition", "contentType", 100)
      val documentSetId = insertCsvImportDocumentSet(uploadedFileId)
      
      val documentSet = DocumentSetLoader.load(documentSetId)
      documentSet must beSome.like { 
        case d =>
          d.documentSetType must be equalTo("CsvImportDocumentSet")
          d.uploadedFileId must beSome.like { case u => u must be equalTo(uploadedFileId) }
      }
    } 
  }

  step(shutdownDb)
}
