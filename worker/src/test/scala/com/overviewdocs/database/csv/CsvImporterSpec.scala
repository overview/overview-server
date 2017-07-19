package com.overviewdocs.csv

import org.specs2.mock.Mockito
import scala.collection.mutable
import scala.concurrent.Future

import com.overviewdocs.metadata.{MetadataField,MetadataFieldType,MetadataSchema}
import com.overviewdocs.models.{CsvImport,Document,DocumentProcessingError}
import com.overviewdocs.models.tables.{CsvImports,Documents,DocumentProcessingErrors,DocumentSets,Tags,Trees}
import com.overviewdocs.database.LargeObject
import com.overviewdocs.searchindex.IndexClient
import com.overviewdocs.test.DbSpecification
import com.overviewdocs.util.AddDocumentsCommon

// A bit of an integration test
class CsvImporterSpec extends DbSpecification with Mockito {
  trait BaseScope extends DbScope {
    import database.api._
    protected implicit val ec = database.executionContext

    val loids = mutable.Buffer[Long]()
    val addDocumentsCommon = smartMock[AddDocumentsCommon]
    addDocumentsCommon.beforeAddDocuments(any) returns Future.unit
    addDocumentsCommon.afterAddDocuments(any) returns Future.unit

    def writeLo(bytes: Array[Byte]): Long = {
      val ret = blockingDatabase.run((for {
        loid <- database.largeObjectManager.create
        lo <- database.largeObjectManager.open(loid, LargeObject.Mode.Write)
        _ <- lo.write(bytes)
      } yield loid).transactionally)
      loids.+=(ret)
      ret
    }

    // Failed tests will leave large objects behind. Ick.
    def cleanup: Unit = {
      blockingDatabase.run((for {
        _ <- DBIO.seq(loids.map(database.largeObjectManager.unlink): _*)
      } yield ()).transactionally)
    }

    val documentSet = factory.documentSet()

    def dbDocuments: Seq[Document] = blockingDatabase.seq(Documents.sortBy(_.id))

    def dbTags = blockingDatabase.seq(Tags.sortBy(_.id))

    def csvImporter(csvImport: CsvImport, bufferSize: Int = 1000): CsvImporter = {
      new CsvImporter(csvImport, addDocumentsCommon, bufferSize)
    }

    def csvImport(bytes: Array[Byte]): CsvImport = {
      val loid = writeLo(bytes)
      factory.csvImport(
        documentSetId=documentSet.id,
        filename="foo.csv",
        loid=loid,
        nBytes=bytes.length
      )
    }

    def cancelAnImport: Unit = {
      val bytes = "text\none\ntwo\nthree".getBytes("utf-8")
      val loid = writeLo(bytes)
      val ci = factory.csvImport(
        documentSetId=documentSet.id,
        filename="foo.csv",
        loid=loid,
        nBytes=bytes.length,
        cancelled=true
      )
      await(csvImporter(ci, 10).run)
    }
  }

  "import documents" in new BaseScope {
    val importer = csvImporter(csvImport("id,text\nHello, world!".getBytes("utf-8")))
    await(importer.run)
    dbDocuments.map((d => (d.suppliedId, d.text))) must beEqualTo(Seq(("Hello", " world!")))
  }

  "work with an empty CSV" in new BaseScope {
    val importer = csvImporter(csvImport(Array[Byte]()))
    await(importer.run)
    dbDocuments must beEmpty
  }

  "delete the large object" in new BaseScope {
    val bytes = "text\n.".getBytes("utf-8")
    val loid = writeLo(bytes)
    val ci = factory.csvImport(documentSetId=documentSet.id, loid=loid, nBytes=bytes.length)
    val importer = csvImporter(ci)
    await(importer.run)

    import database.api._
    blockingDatabase.run((for {
      lo <- database.largeObjectManager.open(loid, LargeObject.Mode.Read)
      _ <- lo.read(1)
    } yield ()).transactionally) must throwA[org.postgresql.util.PSQLException]

    // no cleanup
  }

  "delete the CsvImport" in new BaseScope {
    val ci = csvImport("text\n.".getBytes("utf-8"))
    val importer = csvImporter(ci)
    await(importer.run)

    import database.api._
    blockingDatabase.option(CsvImports) must beNone
  }

  "call AddDocumentsCommon.beforeAddDocuments()" in new BaseScope {
    await(csvImporter(csvImport("text\n.".getBytes("utf-8"))).run)
    there was one(addDocumentsCommon).beforeAddDocuments(documentSet.id)
  }

  "call AddDocumentsCommon.afterAddDocuments()" in new BaseScope {
    await(csvImporter(csvImport("text\n.".getBytes("utf-8"))).run)
    there was one(addDocumentsCommon).afterAddDocuments(documentSet.id)
  }

  "add the metadata columns to the DocumentSet" in new BaseScope {
    await(csvImporter(csvImport("text,foo,bar\n1,2,3".getBytes("utf-8"))).run)
    blockingDatabase.option(DocumentSets).map(_.metadataSchema) must beSome(MetadataSchema(1, Seq(
      MetadataField("foo", MetadataFieldType.String),
      MetadataField("bar", MetadataFieldType.String)
    )))
  }

  "create a Tree" in new BaseScope {
    await(csvImporter(csvImport("text\n.".getBytes("utf-8")).copy(lang="fr")).run)

    import database.api._
    blockingDatabase.option(Trees).map(t => (t.documentSetId, t.lang))
      .must(beSome((documentSet.id, "fr")))
  }

  "update progress in the middle" in new BaseScope {
    val ci = csvImport("text\none potato\ntwo potato\nthree potato".getBytes("utf-8"))
    val importer = csvImporter(ci)
    await(importer.step(10))

    import database.api._
    blockingDatabase.option(CsvImports).map(_.nBytesProcessed) must beSome(10)
  }

  "update progress at the end" in new BaseScope {
    val ci = csvImport("text\n.".getBytes("utf-8"))
    val importer = csvImporter(ci)
    await(importer.step(10))

    import database.api._
    blockingDatabase.option(CsvImports).map(_.nBytesProcessed) must beSome(6)
  }

  "not re-insert a document on resume" in new BaseScope {
    val ci = csvImport("text\none\ntwo\nthree".getBytes("utf-8"))
    await(csvImporter(ci).step(10))               // and then the process dies...
    await(csvImporter(ci.copy(nDocuments=1)).run) // ... and is resumed

    dbDocuments.map(_.text) must beEqualTo(Seq("one", "two", "three"))
  }

  "rewind progress on resume" in new BaseScope {
    val ci = csvImport("text\none\ntwo\nthree".getBytes("utf-8"))
    await(csvImporter(ci).step(15))                    // and then the process dies...
    await(csvImporter(ci.copy(nDocuments=2)).step(10)) // ... and is resumed

    import database.api._
    blockingDatabase.option(CsvImports).map(_.nBytesProcessed) must beSome(10)
  }

  "replace invalid UTF-8" in new BaseScope {
    val data = "text\none\ntwo\nthree".getBytes("utf-8")
    data(5) = 0xfa.toByte
    val ci = csvImport(data)
    await(csvImporter(ci).run)

    dbDocuments.map(_.text) must beEqualTo(Seq("�ne", "two", "three"))
  }

  "reuse existing tags on resume" in new BaseScope {
    val data = "text,tags\none,tag1\ntwo,tag1".getBytes("utf-8")
    val ci = csvImport(data)
    await(csvImporter(ci).step(20))               // write one tag, then the process dies...
    await(csvImporter(ci.copy(nDocuments=1)).run) // ... and is resumed

    dbTags.map(_.name) must beEqualTo(Seq("tag1"))
  }

  "stop writing documents on cancel" in new BaseScope {
    cancelAnImport
    dbDocuments.map(_.text) must beEqualTo(Seq("one"))
  }

  "delete large object on cancel" in new BaseScope {
    cancelAnImport

    import database.api._
    blockingDatabase.run((for {
      lo <- database.largeObjectManager.open(loids.head, LargeObject.Mode.Read)
      _ <- lo.read(1)
    } yield ()).transactionally) must throwA[org.postgresql.util.PSQLException]
  }

  "delete CsvImport on cancel" in new BaseScope {
    cancelAnImport
    import database.api._
    blockingDatabase.option(CsvImports) must beNone
  }

  "add a DocumentProcessingError on cancel" in new BaseScope {
    cancelAnImport
    import database.api._
    blockingDatabase.option(DocumentProcessingErrors.map(_.createAttributes)) must beSome(
      DocumentProcessingError.CreateAttributes(
        documentSet.id,
        "foo.csv",
        "Overview stopped adding documents because you cancelled processing this CSV",
        None,
        None
      )
    )
  }

  "call AddDocumentsCommon.afterAddDocuments() on cancel" in new BaseScope {
    cancelAnImport
    there was one(addDocumentsCommon).afterAddDocuments(documentSet.id)
  }

  "create a Tree on cancel" in new BaseScope {
    cancelAnImport
    blockingDatabase.option(Trees) must beSome
  }

  "add a DocumentProcessingError when CSV is malformed" in new BaseScope {
    val ci = csvImport("text\none\nt\"".getBytes("utf-8"))
    await(csvImporter(ci).run)

    import database.api._
    blockingDatabase.option(DocumentProcessingErrors.map(_.createAttributes)) must beSome(
      DocumentProcessingError.CreateAttributes(
        ci.documentSetId,
        "foo.csv",
        "Overview stopped adding documents because this is not a valid CSV",
        None,
        None
      )
    )
  }

  "work when characters/documents are spread across multiple reads" in new BaseScope {
    val ci = csvImport("text\none🍠\ntwo🍠\nthree🍠\nfour".getBytes("utf-8")) // 🍠 is 4 bytes
    await(csvImporter(ci, bufferSize=9).run)

    dbDocuments.map(_.text) must beEqualTo(Seq("one🍠", "two🍠", "three🍠", "four"))
  }
}
