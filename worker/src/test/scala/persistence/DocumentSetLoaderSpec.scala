package persistence

import anorm._
import anorm.SqlParser
import helpers.DbSpecification
import testutil.DbSetup._

class DocumentSetLoaderSpec extends DbSpecification {
  step(setupDb)

  "DocumentSetLoader" should {

    "load query and title from id" in new DbTestContext {
      val query = "DocumentSetLoaderSpec"
      val title = "From query: " + query

      val documentSetId = insertDocumentSet(query)

      val documentSet = DocumentSetLoader.loadQuery(documentSetId)
      documentSet must beSome.like {
        case d =>
          d.documentSetType must be equalTo("DocumentCloudDocumentSet")
          d.title must be equalTo (title)
          d.query must beSome.like { case q => q must be equalTo (query) }
      }
    }
  }

  step(shutdownDb)
}
