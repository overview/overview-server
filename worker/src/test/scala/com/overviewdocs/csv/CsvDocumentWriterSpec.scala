package com.overviewdocs.csv

import com.overviewdocs.models.{Document,DocumentTag,Tag}
import com.overviewdocs.models.tables.{Documents,DocumentTags,Tags}
import com.overviewdocs.test.DbSpecification
import com.overviewdocs.util.BulkDocumentWriter

class CsvDocumentWriterSpec extends DbSpecification {
  trait BaseScope extends DbScope {
    val documentSet = factory.documentSet()
    val firstDocumentId = documentSet.id << 32L
    lazy val writer = new CsvDocumentWriter(documentSet.id, None, Seq(), BulkDocumentWriter.forDatabase)

    val doc = CsvDocument("", "", "", Set(), "", Seq()) // Sample document

    def dbDocuments: Seq[Document] = {
      import database.api._
      blockingDatabase.seq(Documents.sortBy(_.id))
    }

    def dbTags: Seq[Tag] = {
      import database.api._
      blockingDatabase.seq(Tags.sortBy(_.id))
    }

    def dbDocumentTags: Seq[(Long,String)] = {
      import database.api._

      blockingDatabase.run(sql"""
        SELECT dt.document_id, t.name
        FROM document_tag dt
        INNER JOIN tag t ON dt.tag_id = t.id
        ORDER BY dt.document_id, t.name
      """.as[(Long,String)])
    }
  }

  "CsvDocumentWriter" should {
    "not write documents before flush" in new BaseScope {
      writer.add(doc)
      dbDocuments must beEmpty
    }

    "write documents on flush" in new BaseScope {
      writer.add(doc)
      await(writer.flush)
      dbDocuments.map(_.id) must beEqualTo(Seq(firstDocumentId))
    }

    "work after a flush" in new BaseScope {
      writer.add(doc)
      await(writer.flush)
      writer.add(doc)
      await(writer.flush)
      dbDocuments.map(_.id) must beEqualTo(Seq(firstDocumentId, firstDocumentId + 1))
    }

    "work when flushing nothing" in new BaseScope {
      await(writer.flush) must beEqualTo(())
      dbDocuments must beEmpty
    }

    "write Tags" in new BaseScope {
      writer.add(doc.copy(tags=Set("foo", "bar")))
      await(writer.flush)
      dbTags.map(_.name).toSet must beEqualTo(Set("foo", "bar"))
    }

    "write DocumentTags" in new BaseScope {
      writer.add(doc.copy(tags=Set("foo", "bar")))
      await(writer.flush)
      dbDocumentTags must beEqualTo(Seq(firstDocumentId -> "bar", firstDocumentId -> "foo"))
    }

    "reuse existing tags on different documents" in new BaseScope {
      writer.add(doc.copy(tags=Set("foo", "bar")))
      writer.add(doc.copy(tags=Set("bar", "baz")))
      await(writer.flush)
      dbTags.map(_.name).toSet must beEqualTo(Set("foo", "bar", "baz"))
      dbDocumentTags must beEqualTo(Seq(
        firstDocumentId -> "bar",
        firstDocumentId -> "foo",
        firstDocumentId + 1 -> "bar",
        firstDocumentId + 1 -> "baz"
      ))
    }

    "reuse existing tags across flushes" in new BaseScope {
      writer.add(doc.copy(tags=Set("foo", "bar")))
      await(writer.flush)
      writer.add(doc.copy(tags=Set("bar", "baz")))
      await(writer.flush)
      dbTags.map(_.name).toSet must beEqualTo(Set("foo", "bar", "baz"))
      dbDocumentTags must beEqualTo(Seq(
        firstDocumentId -> "bar",
        firstDocumentId -> "foo",
        firstDocumentId + 1 -> "bar",
        firstDocumentId + 1 -> "baz"
      ))
    }

    "truncate (and reuse) tags >100chars" in new BaseScope {
      val tag = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      val tag1 = tag + "1"
      val tag2 = tag + "2"
      writer.add(doc.copy(tags=Set(tag1)))
      writer.add(doc.copy(tags=Set(tag, tag2)))
      await(writer.flush)
      dbTags.map(_.name).toSet must beEqualTo(Set(tag))
      dbDocumentTags must beEqualTo(Seq(
        firstDocumentId -> tag,
        firstDocumentId + 1 -> tag
      ))
    }

    "start after the given document ID" in new BaseScope {
      override lazy val writer = new CsvDocumentWriter(documentSet.id, Some(4L), Seq(), BulkDocumentWriter.forDatabase)
      writer.add(doc)
      writer.add(doc)
      await(writer.flush)
      dbDocuments.map(_.id) must beEqualTo(Seq(5L, 6L))
    }

    "use existing tags" in new BaseScope {
      val tag = factory.tag(documentSetId=documentSet.id, name="foo")
      override lazy val writer = new CsvDocumentWriter(documentSet.id, None, Seq(tag), BulkDocumentWriter.forDatabase)
      writer.add(doc.copy(tags=Set("foo", "bar")))
      await(writer.flush)
      dbTags.length must beEqualTo(2)
    }
  }
}
