package controllers.api

import play.api.libs.json.Json
import scala.concurrent.Future

import controllers.backend.{DocumentBackend,SelectionBackend}
import models.pagination.{Page,PageInfo,PageRequest}
import models.{Selection,SelectionRequest}
import org.overviewproject.models.DocumentInfo

class DocumentControllerSpec extends ApiControllerSpecification {
  trait BaseScope extends ApiControllerScope {
    val mockSelectionBackend = mock[SelectionBackend]
    val mockDocumentBackend = mock[DocumentBackend]
    val controller = new DocumentController {
      override val documentBackend = mockDocumentBackend
      override val selectionBackend = mockSelectionBackend
    }
  }

  "DocumentController" should {
    "#index" should {
      trait IndexScope extends BaseScope {
        val documentSetId = 1L
        val q = ""
        val fields = ""
        val pageRequest = PageRequest(0, 1000)
        def emptyPage[T] = Page(Seq[T](), PageInfo(pageRequest, 0))

        override lazy val request = fakeRequest("GET", "?q=" + q)
        override def action = controller.index(documentSetId, fields)

        val selectedIds: Seq[Long] = Seq()
        val selection = Selection(SelectionRequest(documentSetId, q=q), selectedIds)

        mockSelectionBackend.create(any, any) returns Future.successful(selection)
        mockSelectionBackend.findOrCreate(any, any) returns Future.successful(selection)
        mockDocumentBackend.index(any, any) returns Future.successful(emptyPage[DocumentInfo])
      }

      "return JSON with status code 200" in new IndexScope {
        status(result) must beEqualTo(OK)
        contentType(result) must beSome("application/json")
      }

      "return an empty Array when there are no Documents" in new IndexScope {
        contentAsString(result) must /("pagination") /("total" -> 0)
      }

      "grab selectionRequest from the HTTP request" in new IndexScope {
        override val q = "foo"
        status(result)
        there was one(mockSelectionBackend).create(request.apiToken.createdBy, SelectionRequest(documentSetId, q="foo"))
      }

      "grab pageRequest from the HTTP request" in new IndexScope {
        override lazy val request = fakeRequest("GET", "/?offset=1&limit=2")
        status(result)
        there was one(mockDocumentBackend).index(selection, PageRequest(1, 2))
      }

      "return an Array of IDs when fields=id" in new IndexScope {
        override val fields = "id"
        mockDocumentBackend.indexIds(any) returns Future(Seq(1L, 2L, 3L))
        status(result) must beEqualTo(OK)
        contentType(result) must beSome("application/json")
        contentAsString(result) must beEqualTo("[1,2,3]")
      }

      trait IndexFieldsScope extends IndexScope {
        val documents = Seq(
          factory.document(
            title="foo",
            keywords=Seq("foo", "bar"),
            suppliedId="supplied 1",
            text="text",
            url=Some("http://example.org"),
            pageNumber=Some(1)
          ),
          factory.document(title="", keywords=Seq(), suppliedId="", url=None)
        )
        mockDocumentBackend.index(any, any) returns Future.successful(
          Page(documents.map(_.toDocumentInfo), PageInfo(PageRequest(0, 100), documents.length))
        )
      }

      "return some Documents when there are Documents" in new IndexFieldsScope {
        val json = contentAsString(result)

        json must /("items") /#(0) /("id" -> documents(0).id)
        json must /("items") /#(0) /("title" -> "foo")
        json must /("items") /#(0) /("keywords") /#(0) /("foo")
        json must /("items") /#(0) /("keywords") /#(1) /("bar")
        json must /("items") /#(0) /("suppliedId" -> "supplied 1")
        json must /("items") /#(0) /("url" -> "http://example.org")
        json must /("items") /#(0) /("pageNumber" -> 1)
        // specs2 foils me on this one:
        // json must not /("items") /#(0) /("text")

        json must /("items") /#(1) /("id" -> documents(1).id)
        json must /("items") /#(1) /("title" -> "")
        // I can't get these past specs2:
        // json must /("items") /#(1) /("keywords" -> beEmpty[Seq[Any]])
        // json must not /("items") /#(1) /("suppliedId")
        // json must not /("items") /#(1) /("url")
        // json must not /("items") /#(1) /("text")
      }

      "return specified fields (1/2)" in new IndexFieldsScope {
        override val fields = "id,documentSetId,url,suppliedId,title"

        status(result) must beEqualTo(OK)
        val json = contentAsString(result)

        json must /("items") /#(0) /("id" -> documents(0).id)
        json must /("items") /#(0) /("documentSetId" -> documents(0).documentSetId)
        json must /("items") /#(0) /("url" -> "http://example.org")
        json must /("items") /#(0) /("suppliedId" -> "supplied 1")
        json must /("items") /#(0) /("title" -> "foo")
        json must not(beMatching("keywords".r))
        json must not(beMatching("pageNumber".r))
      }

      "return specified fields (2/2)" in new IndexFieldsScope {
        override val fields = "id,keywords,pageNumber"

        status(result) must beEqualTo(OK)
        val json = contentAsString(result)

        json must /("items") /#(0) /("id" -> documents(0).id)
        json must /("items") /#(0) /("keywords") /#(0) /("foo")
        json must /("items") /#(0) /("keywords") /#(1) /("bar")
        json must /("items") /#(0) /("pageNumber" -> 1)
        json must not(beMatching("documentSetId".r))
        json must not(beMatching("url".r))
        json must not(beMatching("suppliedId".r))
        json must not(beMatching("title".r))
      }

      "always return id, even if it is not in the fields" in new IndexFieldsScope {
        override val fields = "suppliedId"

        status(result) must beEqualTo(OK)
        val json = contentAsString(result)

        json must /("items") /#(0) /("id" -> documents(0).id)
        json must /("items") /#(0) /("suppliedId" -> "supplied 1")
        json must not(beMatching("url".r))
      }

      "ignore invalid fields" in new IndexFieldsScope {
        override val fields = "id,title,bleep"

        status(result) must beEqualTo(OK)
        val json = contentAsString(result)

        json must /("items") /#(0) /("id" -> documents(0).id)
        json must /("items") /#(0) /("title" -> "foo")
        json must not(beMatching("bleep".r))
      }
    }

    "#show" should {
      trait ShowScope extends BaseScope {
        val documentSetId = 1L
        val documentId = 2L

        override def action = controller.show(documentSetId, documentId)
      }

      "return 404 when not found" in new ShowScope {
        mockDocumentBackend.show(documentSetId, documentId) returns Future(None)
        status(result) must beEqualTo(NOT_FOUND)
        contentType(result) must beSome("application/json")
        val json = contentAsString(result)
        json must /("message" -> s"Document $documentId not found in document set $documentSetId")
      }

      "return JSON with status code 200" in new ShowScope {
        mockDocumentBackend.show(documentSetId, documentId) returns Future(Some(factory.document(
          id=documentId,
          documentSetId=documentSetId,
          keywords=Seq("foo", "bar"),
          url=Some("http://example.org"),
          text="text",
          title="title",
          suppliedId="suppliedId"
        )))

        status(result) must beEqualTo(OK)
        contentType(result) must beSome("application/json")

        val json = contentAsString(result)

        json must /("id" -> documentId)
        json must not /("documentSetId")
        json must /("keywords") /#(0) /("foo")
        json must /("keywords") /#(1) /("bar")
        json must /("url" -> "http://example.org")
        json must /("title" -> "title")
        json must /("text" -> "text")
        json must /("suppliedId" -> "suppliedId")
      }
    }
  }
}
