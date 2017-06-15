package controllers.backend

import com.overviewdocs.models.CsvImport
import com.overviewdocs.models.tables.CsvImports

class DbCsvImportBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbBackendScope {
    val backend = new DbCsvImportBackend(injectedDatabase)
  }

  "#create" should {
    trait CreateScope extends BaseScope {
      val documentSet = factory.documentSet()
    }

    "return a CsvImport" in new CreateScope {
      val csvImport: CsvImport = await(backend.create(CsvImport.CreateAttributes(
        documentSetId=documentSet.id,
        filename="filename.csv",
        charsetName="windows-1252",
        lang="fr",
        loid=123L,
        nBytes=1000L
      )))

      csvImport.documentSetId must beEqualTo(documentSet.id)
      csvImport.filename must beEqualTo("filename.csv")
      csvImport.charset.name must beEqualTo("windows-1252")
      csvImport.lang must beEqualTo("fr")
      csvImport.loid must beEqualTo(123L)
      csvImport.nBytes must beEqualTo(1000L)
      csvImport.nBytesProcessed must beEqualTo(0L)
      csvImport.cancelled must beEqualTo(false)
    }

    "write the CsvImport to the database" in new CreateScope {
      val csvImport: CsvImport = await(backend.create(CsvImport.CreateAttributes(
        documentSetId=documentSet.id,
        filename="filename.csv",
        charsetName="windows-1252",
        lang="fr",
        loid=123L,
        nBytes=1000L
      )))
      val dbCsvImport = blockingDatabase.option(CsvImports)

      dbCsvImport must beSome(csvImport)
    }
  }

  "#cancel" should {
    trait CancelScope extends BaseScope {
      import database.api._
      val documentSet = factory.documentSet()
      def dbValue(id: Long): CsvImport = blockingDatabase.option(CsvImports.filter(_.id === id)).get
    }

    "cancel a CsvImport" in new CancelScope {
      val csvImport = factory.csvImport(documentSetId=documentSet.id)
      await(backend.cancel(documentSet.id, csvImport.id)) must beTrue
      dbValue(csvImport.id).cancelled must beTrue
    }

    "not cancel for a different job" in new CancelScope {
      val csvImport = factory.csvImport(documentSetId=documentSet.id)
      val csvImport2 = factory.csvImport(documentSetId=documentSet.id)
      await(backend.cancel(documentSet.id, csvImport2.id)) must beTrue
      dbValue(csvImport.id).cancelled must beFalse
    }

    "not cancel for a different document set" in new CancelScope {
      val documentSet2 = factory.documentSet()
      val csvImport = factory.csvImport(documentSetId=documentSet.id)
      await(backend.cancel(documentSet2.id, csvImport.id)) must beFalse
      dbValue(csvImport.id).cancelled must beFalse
    }

    "not cancel when the import is complete" in new CancelScope {
      val csvImport = factory.csvImport(documentSetId=documentSet.id, nBytesProcessed=1L, nBytes=1L)
      await(backend.cancel(documentSet.id, csvImport.id)) must beFalse
      dbValue(csvImport.id).cancelled must beFalse
    }
  }
}
