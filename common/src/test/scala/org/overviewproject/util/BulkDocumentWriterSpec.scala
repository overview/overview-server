package org.overviewproject.util

import play.api.libs.json.{Json,JsObject}

import org.overviewproject.metadata.MetadataSchema
import org.overviewproject.models.tables.{DocumentSets,Documents,Files,Pages}
import org.overviewproject.models.{Document,DocumentSet,File,Page}
import org.overviewproject.test.DbSpecification
import org.overviewproject.models.DocumentDisplayMethod

class BulkDocumentWriterSpec extends DbSpecification {
  sequential

  trait BaseScope extends DbScope {
    import database.api._

    protected val emptySha1 = Array(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0).map(_.toByte)

    val documentSet: DocumentSet = {
      blockingDatabase.run((DocumentSets returning DocumentSets).+=(DocumentSet(
        1L,
        "",
        None,
        false,
        new java.sql.Timestamp(1424898930910L),
        0,
        0,
        0,
        None,
        MetadataSchema.empty,
        false
      )))
    }

    def fetchDocuments: Seq[Document] = {
      blockingDatabase.seq(Documents.filter(_.documentSetId === documentSet.id))
    }

    var nFlushes = 0

    val subject = new BulkDocumentWriter {
      override val maxNDocuments = 3
      override val maxNBytes = 1000

      override def flushImpl(documents: Iterable[Document]) = {
        nFlushes += 1
        flushDocumentsToDatabase(database, documents)
      }
    }

    object factory {
      def document(id: Long, text: String) = Document(
        id,
        documentSet.id,
        Some("http://18-byte-url"),
        "18-char-suppliedId",
        "13-char-title",
        None,
        Seq(),
        new java.util.Date(documentSet.createdAt.getTime()),
        None,
        None,
        None,
        JsObject(Seq()),
        text
      )
    }

    def add(document: Document): Document = {
      await(subject.addAndFlushIfNeeded(document))
      document
    }

    // Convenience method. Adds and returns a Document with text
    def addText(id: Long, text: String): Document = add(factory.document(id, text))
  }

  "should not flush when empty" in new BaseScope {
    await(subject.flush)
    nFlushes must beEqualTo(0)
    fetchDocuments must beEmpty
  }

  "should flush when non-empty" in new BaseScope {
    val doc1 = addText(1L, "foobar")
    fetchDocuments.length must beEqualTo(0)
    await(subject.flush)
    nFlushes must beEqualTo(1)
    fetchDocuments.length must beEqualTo(1)
  }

  "should empty when flushing" in new BaseScope {
    addText(1L, "foobar")
    await(subject.flush)
    await(subject.flush)
    nFlushes must beEqualTo(1)
    fetchDocuments.length must beEqualTo(1)
  }

  "flush on add when adding more than N documents" in new BaseScope {
    addText(1L, "doc1")
    addText(2L, "doc2")
    fetchDocuments.length must beEqualTo(0)
    addText(3L, "doc3")
    fetchDocuments.length must beEqualTo(3)
  }

  "flush on add when adding more than N bytes" in new BaseScope {
    addText(1L, "doc1")
    fetchDocuments.length must beEqualTo(0)
    addText(2L, """
      mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm
      mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm
      mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm
      mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm
      mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm
      mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm
      mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm
      mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm
      mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm
      mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm
    """)
    fetchDocuments.length must beEqualTo(2)
  }

  "handle NULLs when writing" in new BaseScope {
    add(factory.document(1L, "").copy(url=None, pageNumber=None, fileId=None, pageId=None))
    await(subject.flush)
    val doc = fetchDocuments(0)
    doc.url must beNone
    doc.pageNumber must beNone
    doc.fileId must beNone
    doc.pageId must beNone
  }

  "handle non-NULLs when writing" in new BaseScope {
    import database.api._
    blockingDatabase.runUnit(Files.+=(File(3L, 0, "", "", 0, emptySha1, "", 0)))
    blockingDatabase.runUnit(Pages.+=(Page(4L, 3L, 0, "", 0, None, None, None, None)))
    add(factory.document(2L, "").copy(url=Some("http://example.org"), pageNumber=Some(5), fileId=Some(3L), pageId=Some(4L)))
    await(subject.flush)
    val doc = fetchDocuments(0)
    doc.url must beSome("http://example.org")
    doc.pageNumber must beSome(5)
    doc.fileId must beSome(3L)
    doc.pageId must beSome(4L)
  }

  "handle utf-8 text when writing" in new BaseScope {
    val text = "ᚠᛇᚻ᛫ᛒᛦᚦ᛫ᚠᚱᚩᚠᚢᚱ᛫ᚠᛁᚱᚪ᛫ᚷᛖᚻᚹᛦᛚᚳᚢᛗ\nLaȝamon\nΤη γλώσσα μου έδωσαν ελληνική\nನಿತ್ಯವೂ ಅವತರಿಪ ಸತ್ಯಾವ"
    add(factory.document(2L, text))
    await(subject.flush)
    fetchDocuments(0).text must beEqualTo(text)
  }

  "handle keywords" in new BaseScope {
    val keywords = Seq("foo", "bar", "baz")
    add(factory.document(1L, "").copy(keywords=keywords))
    await(subject.flush)
    fetchDocuments(0).keywords must beEqualTo(keywords)
  }

  "handle displayMethod" in new BaseScope {
    val displayMethod = DocumentDisplayMethod.auto  
    add(factory.document(1L, "").copy(displayMethod = Some(displayMethod)))
    await(subject.flush)
    
    fetchDocuments(0).displayMethod must beSome(displayMethod)
  }

  "handle metadataJson" in new BaseScope {
    val metadataJson = Json.obj("foo" -> "bar")
    add(factory.document(1L, "").copy(metadataJson = metadataJson))
    await(subject.flush)

    fetchDocuments(0).metadataJson must beEqualTo(metadataJson)
  }
  
  "handle the other fields" in new BaseScope {
    add(factory.document(2L, "").copy(documentSetId=1L, suppliedId="suppl", title="title"))
    await(subject.flush)
    val doc = fetchDocuments(0)
    doc.documentSetId must beEqualTo(1L)
    doc.suppliedId must beEqualTo("suppl")
    doc.title must beEqualTo("title")
  }
}
