package persistence

import anorm._
import anorm.SqlParser._
import org.overviewproject.test.DbSpecification
import testutil.DbSetup._

class PersistentCsvImportDocumentSpec extends DbSpecification {
  step(setupDb)

  trait Setup extends DbTestContext {
    var documentSetId: Long = _
    val documentText = "some text"

    override def setupWithDb = documentSetId = insertDocumentSet("PersistentCsvImportDocumentSpec")
  }

  trait DocumentWithNoId extends Setup {
    val document = new PersistentCsvImportDocument {
      val text = documentText
      val suppliedId = None
      val url = None
    }

    var id: Long = _
    override def setupWithDb = {
      super.setupWithDb
      id = document.write(documentSetId)
    }
  }

  trait DocumentWithAllValues extends Setup {
    val document = new PersistentCsvImportDocument {
      val text = documentText
      val suppliedId = Some("user id")
      val url = Some("url")
    }

    var id: Long = _
    override def setupWithDb = {
      super.setupWithDb
      id = document.write(documentSetId)
    }
  }
  
  trait DocumentWithUrl

  "PersistentCsvImportDocument" should {

    "write text to document table" in new DocumentWithNoId {
      val documents =
        SQL("SELECT id, text FROM document").as(long("id") ~ str("text") map (flatten) *)

      documents must haveTheSameElementsAs(Seq((id, documentText)))
    }

    "leave supplied_id empty if not set" in new DocumentWithNoId {
      val documents =
        SQL("SELECT id, supplied_id FROM document").as(long("id") ~ (str("supplied_id")?) map (flatten) *)

      documents must haveTheSameElementsAs(Seq((id, None)))
    }

    "write supplied_id if set" in new DocumentWithAllValues {
      val documents =
        SQL("SELECT id, supplied_id FROM document").as(long("id") ~ (str("supplied_id")?) map (flatten) *)
      documents must haveTheSameElementsAs(Seq((id, document.suppliedId)))
    }

    "write url if set" in new DocumentWithAllValues {
      val documents =
        SQL("SELECT id, url FROM document").as(long("id") ~ (str("url")?) map (flatten) *)
      documents must haveTheSameElementsAs(Seq((id, document.url)))
    }
  }

  step(shutdownDb)
}