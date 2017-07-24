package com.overviewdocs.util

import play.api.libs.json.{Json,JsObject}

import com.overviewdocs.metadata.MetadataSchema
import com.overviewdocs.models.tables.{DocumentSets,Documents,Files,Pages}
import com.overviewdocs.models.{Document,DocumentSet,File,Page}
import com.overviewdocs.test.DbSpecification
import com.overviewdocs.models.DocumentDisplayMethod

class BulkDocumentWriterSpec extends DbSpecification {
  sequential

  trait BaseScope extends DbScope {
    import database.api._

    protected val emptySha1 = Array(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0).map(_.toByte)

    val documentSet = factory.documentSet()

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

    def add(document: Document): Document = {
      await(subject.addAndFlushIfNeeded(document))
      document
    }

    // Convenience method. Adds and returns a Document with text
    def addText(text: String): Document = add(podoFactory.document(documentSetId=documentSet.id, text=text))
  }

  "should not flush when empty" in new BaseScope {
    await(subject.flush)
    nFlushes must beEqualTo(0)
    fetchDocuments must beEmpty
  }

  "should flush when non-empty" in new BaseScope {
    val doc1 = addText("foobar")
    fetchDocuments.length must beEqualTo(0)
    await(subject.flush)
    nFlushes must beEqualTo(1)
    fetchDocuments.length must beEqualTo(1)
  }

  "should empty when flushing" in new BaseScope {
    addText("foobar")
    await(subject.flush)
    await(subject.flush)
    nFlushes must beEqualTo(1)
    fetchDocuments.length must beEqualTo(1)
  }

  "flush on add when adding more than N documents" in new BaseScope {
    addText("doc1")
    addText("doc2")
    fetchDocuments.length must beEqualTo(0)
    addText("doc3")
    fetchDocuments.length must beEqualTo(3)
  }

  "flush on add when adding more than N bytes" in new BaseScope {
    addText("doc1")
    fetchDocuments.length must beEqualTo(0)
    addText("""
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
    add(podoFactory.document(documentSetId=documentSet.id, text="", url=None, pageNumber=None, fileId=None, pageId=None))
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
    blockingDatabase.runUnit(Pages.+=(Page(4L, 3L, 0, "", 0, "", false)))
    add(podoFactory.document(documentSetId=documentSet.id, url=Some("http://example.org"), pageNumber=Some(5), fileId=Some(3L), pageId=Some(4L)))
    await(subject.flush)
    val doc = fetchDocuments(0)
    doc.url must beSome("http://example.org")
    doc.pageNumber must beSome(5)
    doc.fileId must beSome(3L)
    doc.pageId must beSome(4L)
  }

  "handle utf-8 text when writing" in new BaseScope {
    val text = "ᚠᛇᚻ᛫ᛒᛦᚦ᛫ᚠᚱᚩᚠᚢᚱ᛫ᚠᛁᚱᚪ᛫ᚷᛖᚻᚹᛦᛚᚳᚢᛗ\nLaȝamon\nΤη γλώσσα μου έδωσαν ελληνική\nನಿತ್ಯವೂ ಅವತರಿಪ ಸತ್ಯಾವ"
    add(podoFactory.document(documentSetId=documentSet.id, text=text))
    await(subject.flush)
    fetchDocuments(0).text must beEqualTo(text)
  }

  "handle displayMethod" in new BaseScope {
    add(podoFactory.document(documentSetId=documentSet.id, displayMethod = DocumentDisplayMethod.auto))
    await(subject.flush)
    
    fetchDocuments(0).displayMethod must beEqualTo(DocumentDisplayMethod.auto)
  }

  "handle isFromOcr=false" in new BaseScope {
    add(podoFactory.document(documentSetId=documentSet.id, isFromOcr=false))
    await(subject.flush)

    fetchDocuments(0).isFromOcr must beEqualTo(false)
  }

  "handle isFromOcr=true" in new BaseScope {
    add(podoFactory.document(documentSetId=documentSet.id, isFromOcr=true))
    await(subject.flush)

    fetchDocuments(0).isFromOcr must beEqualTo(true)
  }

  "handle metadataJson" in new BaseScope {
    val metadataJson = Json.obj("foo" -> "bar")
    add(podoFactory.document(documentSetId=documentSet.id, metadataJson = metadataJson))
    await(subject.flush)

    fetchDocuments(0).metadataJson must beEqualTo(metadataJson)
  }
  
  "handle the other fields" in new BaseScope {
    add(podoFactory.document(documentSetId=documentSet.id, suppliedId="suppl", title="title"))
    await(subject.flush)
    val doc = fetchDocuments(0)
    doc.documentSetId must beEqualTo(documentSet.id)
    doc.suppliedId must beEqualTo("suppl")
    doc.title must beEqualTo("title")
  }
}
