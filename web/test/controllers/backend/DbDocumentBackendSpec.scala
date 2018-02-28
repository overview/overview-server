package controllers.backend

import akka.stream.scaladsl.{Sink,Source}
import org.specs2.mock.Mockito
import play.api.libs.json.{JsObject,Json}
import play.api.Configuration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import models.pagination.{Page,PageInfo,PageRequest}
import models.Selection
import com.overviewdocs.models.{Document,PdfNote,PdfNoteCollection}
import com.overviewdocs.models.tables.Documents
import com.overviewdocs.query.{Field,PhraseQuery,Query}

import com.overviewdocs.test.ActorSystemContext

class DbDocumentBackendSpec extends DbBackendSpecification with Mockito {
  trait BaseScope extends DbBackendScope {
    import database.api._
    def findDocument(id: Long): Option[Document] = blockingDatabase.option(Documents.filter(_.id === id))

    val searchBackend = smartMock[SearchBackend]
    searchBackend.refreshDocument(any, any) returns Future.unit

    val backend = new DbDocumentBackend(
      database,
      searchBackend,
      Configuration("overview.n_documents_per_stream_packet" -> 3)
    )
  }

  "DbDocumentBackendSpec" should {
    "#index" should {
      trait IndexScope extends BaseScope {
        val documentSet = factory.documentSet()
        val doc1 = factory.document(documentSetId=documentSet.id, title="c", text="foo bar baz oneandtwo oneandthree")
        val doc2 = factory.document(documentSetId=documentSet.id, title="a", text="moo mar maz oneandtwo twoandthree")
        val doc3 = factory.document(documentSetId=documentSet.id, title="b", text="noo nar naz oneandthree twoandthree")
        val documents = Vector(doc1, doc2, doc3)

        val selection = smartMock[Selection]
        val pageRequest = PageRequest(0, 1000, false)
        val includeText = false
        selection.getDocumentIds(pageRequest) returns Future.successful(Page(Vector(doc2.id, doc3.id, doc1.id), PageInfo(pageRequest, 3)))
        lazy val ret = await(backend.index(selection, pageRequest, includeText))
      }

      "show all documents" in new IndexScope {
        ret.items.length must beEqualTo(3)
      }

      "sort documents by title" in new IndexScope {
        // Actually, selection.getDocumentIds() takes care of sorting.
        // Consider this an integration test.
        ret.items.map(_.id) must beEqualTo(Vector(doc2.id, doc3.id, doc1.id))
      }

      "return the correct pageInfo" in new IndexScope {
        ret.pageInfo.total must beEqualTo(3) // the list of IDs
        ret.pageInfo.offset must beEqualTo(0)
        ret.pageInfo.limit must beEqualTo(1000)
      }

      "omit text when includeText=false" in new IndexScope {
        override val includeText = false
        ret.items.map(_.text) must beEqualTo(Vector("", "", ""))
      }

      "include text when includeText=true" in new IndexScope {
        override val includeText = true
        ret.items.map(_.text) must not(beEqualTo(Vector("", "", "")))
      }

      "work with 0 documents" in new IndexScope {
        selection.getDocumentIds(any) returns Future.successful(Page(Vector[Long]()))
        ret.items.length must beEqualTo(0)
        ret.pageInfo.total must beEqualTo(0)
      }
    }

    "#index (document IDs)" should {
      trait IndexScope extends BaseScope {
        val documentSet = factory.documentSet()
        def index(documentIds: Vector[Long]) = await(backend.index(documentSet.id, documentIds))
      }

      "return Documents, in requested-ID order" in new IndexScope {
        val documents = Vector(
          factory.document(documentSetId=documentSet.id, text="foo"),
          factory.document(documentSetId=documentSet.id, text="bar"),
          factory.document(documentSetId=documentSet.id, text="baz")
        )

        index(documents.map(_.id)).map(_.text) must beEqualTo(Vector("foo", "bar", "baz"))
      }

      "work with 0 documents" in new IndexScope {
        index(Vector()) must beEqualTo(Vector())
      }

      "not include non-requested documents" in new IndexScope {
        val doc = factory.document(documentSetId=documentSet.id)
        val notDoc = factory.document(documentSetId=documentSet.id)

        index(Vector(doc.id)) must beEqualTo(Vector(doc))
      }
    }

    "#stream" should {
      trait StreamScope extends BaseScope with ActorSystemContext {
        val documentSet = factory.documentSet()
        def stream(documentIds: Vector[Long]): Vector[Document] = {
          val source: Source[Document, _] = backend.stream(documentSet.id, documentIds)
          await(source.runWith(Sink.collection[Document, Vector[Document]]))
        }
      }

      "return Documents, in requested-ID order" in new StreamScope {
        val documents = Vector(
          factory.document(documentSetId=documentSet.id, text="foo"),
          factory.document(documentSetId=documentSet.id, text="bar"),
          factory.document(documentSetId=documentSet.id, text="baz")
        )

        stream(documents.map(_.id)).map(_.text) must beEqualTo(Vector("foo", "bar", "baz"))
      }

      "paginate through documents, in order" in new StreamScope {
        val documents = Vector(
          factory.document(documentSetId=documentSet.id, text="foo"),
          factory.document(documentSetId=documentSet.id, text="bar"),
          factory.document(documentSetId=documentSet.id, text="baz"),
          factory.document(documentSetId=documentSet.id, text="moo"),
          factory.document(documentSetId=documentSet.id, text="mar"),
          factory.document(documentSetId=documentSet.id, text="maz"),
          factory.document(documentSetId=documentSet.id, text="zoo"),
          factory.document(documentSetId=documentSet.id, text="zar"),
          factory.document(documentSetId=documentSet.id, text="zaz"),
        )

        stream(documents.map(_.id)).map(_.text) must beEqualTo(Vector("foo", "bar", "baz", "moo", "mar", "maz", "zoo", "zar", "zaz"))
      }

      "work with 0 documents" in new StreamScope {
        stream(Vector()) must beEqualTo(Vector())
      }

      "not include non-requested documents" in new StreamScope {
        val doc = factory.document(documentSetId=documentSet.id)
        val notDoc = factory.document(documentSetId=documentSet.id)

        stream(Vector(doc.id)) must beEqualTo(Vector(doc))
      }
    }

    "#show" should {
      trait ShowScope extends BaseScope {
        val documentSet = factory.documentSet()
        val document = factory.document(documentSetId=documentSet.id, title="title", text="text")

        val documentSetId = documentSet.id
        val documentId = document.id

        lazy val ret = await(backend.show(documentSetId, documentId))
      }

      "show a document" in new ShowScope {
        ret must beSome { d: Document =>
          d.id must beEqualTo(document.id)
          d.title must beEqualTo(document.title)
          d.text must beEqualTo(document.text)
        }
      }

      "show a document when the document set ID is not specified" in new ShowScope {
        val doc = await(backend.show(documentId))
        doc must beSome { d: Document =>
          d.id must beEqualTo(document.id)
          d.title must beEqualTo(document.title)
          d.text must beEqualTo(document.text)
        }
      }

      "not show a document with the wrong document set ID" in new ShowScope {
        override val documentSetId = documentSet.id + 1L
        ret must beNone
      }

      "not show a document with the wrong ID" in new ShowScope {
        override val documentId = document.id + 1L
        ret must beNone
      }
    }

    "#updateTitle" should {
      trait UpdateTitleScope extends BaseScope {
        val documentSet = factory.documentSet()
        val document = factory.document(documentSetId=documentSet.id, title="foo")
      }

      "update title" in new UpdateTitleScope {
        await(backend.updateTitle(documentSet.id, document.id, "bar"))
        findDocument(document.id).map(_.title) must beSome("bar")
      }

      "not update title with the wrong document set ID" in new UpdateTitleScope {
        await(backend.updateTitle(documentSet.id + 1, document.id, "bar"))
        findDocument(document.id).map(_.title) must beSome("foo")
      }

      "update the search index" in new UpdateTitleScope {
        await(backend.updateTitle(documentSet.id, document.id, "bar"))
        there was one(searchBackend).refreshDocument(documentSet.id, document.id)
      }
    }

    "#updateMetadataJson" should {
      trait UpdateMetadataJsonScope extends BaseScope {
        val documentSet = factory.documentSet()
        val document = factory.document(documentSetId=documentSet.id, metadataJson=Json.obj("foo" -> "bar"))
      }

      "update metadataJson" in new UpdateMetadataJsonScope {
        await(backend.updateMetadataJson(documentSet.id, document.id, Json.obj("foo" -> "baz")))
        findDocument(document.id).map(_.metadataJson) must beSome(Json.obj("foo" -> "baz"))
      }

      "not update metadataJson with the wrong document set ID" in new UpdateMetadataJsonScope {
        await(backend.updateMetadataJson(documentSet.id + 1L, document.id, Json.obj("foo" -> "baz")))
        findDocument(document.id).map(_.metadataJson) must beSome(Json.obj("foo" -> "bar"))
      }

      "refresh the searchBackend" in new UpdateMetadataJsonScope {
        await(backend.updateMetadataJson(documentSet.id, document.id, Json.obj("foo" -> "baz")))
        there was one(searchBackend).refreshDocument(documentSet.id, document.id)
      }
    }

    "#updatePdfNotes" should {
      trait UpdatePdfNotesScope extends BaseScope {
        val documentSet = factory.documentSet()
        val pdfNotes1 = PdfNoteCollection(Array(
          PdfNote(1, 2.0, 3.0, 4.0, 5.0, "Foo")
        ))
        val pdfNotes2 = PdfNoteCollection(Array(
          PdfNote(1, 2.0, 3.0, 4.0, 5.0, "Foo"),
          PdfNote(2, 3.0, 4.0, 5.0, 6.0, "Bar")
        ))
        // document ID must be derived from documentSetId for this test
        val document = factory.document(documentSetId=documentSet.id, id=documentSet.id << 32, pdfNotes=pdfNotes1)
      }

      "update pdfNotes" in new UpdatePdfNotesScope {
        await(backend.updatePdfNotes(document.documentSetId, document.id, pdfNotes2))
        findDocument(document.id).map(_.pdfNotes) must beSome(pdfNotes2)
      }

      "refresh in searchBackend" in new UpdatePdfNotesScope {
        await(backend.updatePdfNotes(document.documentSetId, document.id, pdfNotes2))
        there was one(searchBackend).refreshDocument(documentSet.id, document.id)
      }
    }
  }
}
