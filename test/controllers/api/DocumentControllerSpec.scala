package controllers.api

import play.api.libs.json.Json
import play.api.mvc.Result
import scala.concurrent.Future

import controllers.auth.ApiAuthorizedRequest
import controllers.backend.{DocumentBackend,DocumentSetBackend}
import models.pagination.{Page,PageInfo,PageRequest}
import models.{InMemorySelection,Selection}
import com.overviewdocs.models.DocumentHeader
import com.overviewdocs.metadata.{MetadataSchema,MetadataField,MetadataFieldType}

class DocumentControllerSpec extends ApiControllerSpecification {
  trait BaseScope extends ApiControllerScope {
    lazy val selection = InMemorySelection(Seq()) // override for a different Selection
    val documentSet = factory.documentSet(metadataSchema = MetadataSchema(1, Seq(
      MetadataField("foo", MetadataFieldType.String)
    )))
    def buildSelection: Future[Either[Result,Selection]] = Future(Right(selection)) // override for edge cases
    val mockDocumentBackend = smartMock[DocumentBackend]
    val mockDocumentSetBackend = smartMock[DocumentSetBackend]
    mockDocumentSetBackend.show(documentSet.id) returns Future.successful(Some(documentSet))
    val controller = new DocumentController with TestController {
      override val documentBackend = mockDocumentBackend
      override val documentSetBackend = mockDocumentSetBackend
      override def requestToSelection(documentSetId: Long, request: ApiAuthorizedRequest[_]) = buildSelection
    }
  }

  "DocumentController" should {
    "#index" should {
      trait IndexScope extends BaseScope {
        val documentSetId = documentSet.id
        val q = ""
        val fields = ""
        val pageRequest = PageRequest(0, 1000)
        def emptyPage[T] = Page(Seq[T](), PageInfo(pageRequest, 0))

        override lazy val request = fakeRequest("GET", "?q=" + q)
        override def action = controller.index(documentSetId, fields)

        mockDocumentBackend.index(any, any, any) returns Future.successful(emptyPage[DocumentHeader])
      }

      "return JSON with status code 200" in new IndexScope {
        status(result) must beEqualTo(OK)
        contentType(result) must beSome("application/json")
      }

      "return an empty Array when there are no Documents" in new IndexScope {
        contentAsString(result) must /("pagination") /("total" -> 0)
      }

      "grab pageRequest from the HTTP request" in new IndexScope {
        override lazy val request = fakeRequest("GET", "/?offset=1&limit=2")
        status(result)
        there was one(mockDocumentBackend).index(selection, PageRequest(1, 2), false)
      }

      "set page limit to 1000" in new IndexScope {
        override lazy val request = fakeRequest("GET", "/?offset=1&limit=9999999")
        status(result)
        there was one(mockDocumentBackend).index(selection, PageRequest(1, 1000), false)
      }

      "set page limit to 20 when requesting text" in new IndexScope {
        override lazy val request = fakeRequest("GET", "/?offset=1&limit=9999999")
        override val fields = "id,text"
        status(result)
        there was one(mockDocumentBackend).index(selection, PageRequest(1, 20), true)
      }

      "set page limit to 20 when requesting metadata" in new IndexScope {
        override lazy val request = fakeRequest("GET", "/?offset=1&limit=9999999")
        override val fields = "id,metadata"
        status(result)
        there was one(mockDocumentBackend).index(selection, PageRequest(1, 20), true)
      }

      "set page limit to 20 when requesting tokens" in new IndexScope {
        override lazy val request = fakeRequest("GET", "/?offset=1&limit=9999999")
        override val fields = "id,tokens"
        status(result)
        there was one(mockDocumentBackend).index(selection, PageRequest(1, 20), true)
      }

      "return an Array of IDs when fields=id" in new IndexScope {
        override val fields = "id"
        override lazy val selection = InMemorySelection(Seq(1L, 2L, 3L))
        status(result) must beEqualTo(OK)
        contentType(result) must beSome("application/json")
        contentAsString(result) must beEqualTo("[1,2,3]")
      }

      "return the selectionId" in new IndexScope {
        override lazy val selection = InMemorySelection(Seq(1L, 2L, 3L))
        contentAsString(result) must /("selectionId" -> selection.id.toString)
      }

      trait IndexFieldsScope extends IndexScope {
        lazy val documents = List(
          factory.document(
            title="foo",
            keywords=Seq("foo", "bar"),
            suppliedId="supplied 1",
            text="text",
            metadataJson=Json.obj("foo" -> "bar"),
            url=Some("http://example.org"),
            pageNumber=Some(1)
          ),
          factory.document(title="", keywords=Seq(), suppliedId="", url=None)
        )
        override lazy val selection = InMemorySelection(documents.map(_.id))
        mockDocumentBackend.index(any, any, any) returns Future(
          Page(documents, PageInfo(PageRequest(0, 100), documents.length))
        )
      }

      "return some Documents when there are Documents" in new IndexFieldsScope {
        val json = contentAsString(result)

        // specs2 doesn't have a Long JSON matcher
        //json must /("items") /#(0) /("id" -> documents(0).id)
        json must /("items") /#(0) /("title" -> "foo")
        json must /("items") /#(0) /("keywords") /#(0) /("foo")
        json must /("items") /#(0) /("keywords") /#(1) /("bar")
        json must /("items") /#(0) /("suppliedId" -> "supplied 1")
        json must /("items") /#(0) /("url" -> "http://example.org")
        json must /("items") /#(0) /("pageNumber" -> 1)
        json must not /("items") /#(0) /("text")
        json must not /("items") /#(0) /("metadata")

        //json must /("items") /#(1) /("id" -> documents(1).id)
        json must /("items") /#(1) /("title" -> "")
        //json must /("items") /#(1) /("keywords")
        //json must not /("items") /#(1) /("keywords") /#(0)
        json must not /("items") /#(1) /("suppliedId")
        json must not /("items") /#(1) /("url")
        json must not /("items") /#(1) /("text")
        json must not /("items") /#(1) /("metadata")
      }

      "return specified fields (1/2)" in new IndexFieldsScope {
        override val fields = "id,documentSetId,url,suppliedId,title"

        status(result) must beEqualTo(OK)
        val json = contentAsString(result)

        // specs2 doesn't have a Long JSON matcher
        //json must /("items") /#(0) /("id" -> documents(0).id)
        json must /("items") /#(0) /("documentSetId" -> documents(0).documentSetId.toInt)
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

        // specs2 doesn't have a Long JSON matcher
        //json must /("items") /#(0) /("id" -> documents(0).id)
        json must /("items") /#(0) /("keywords") /#(0) /("foo")
        json must /("items") /#(0) /("keywords") /#(1) /("bar")
        json must /("items") /#(0) /("pageNumber" -> 1)
        json must not(beMatching("documentSetId".r))
        json must not(beMatching("url".r))
        json must not(beMatching("suppliedId".r))
        json must not(beMatching("title".r))
      }

      "query for text when fields includes text" in new IndexFieldsScope {
        override lazy val request = fakeRequest("GET", "/?offset=1&limit=2")
        override val fields = "id,text"

        status(result) must beEqualTo(OK)
        contentAsString(result) must /("items") /#(0) /("text" -> "text")
        there was one(mockDocumentBackend).index(selection, PageRequest(1, 2), true)
      }

      "query for tokens when fields includes tokens" in new IndexFieldsScope {
        override lazy val request = fakeRequest("GET", "/?offset=1&limit=2")
        override val fields = "id,tokens"

        status(result) must beEqualTo(OK)
        contentAsString(result) must /("items") /#(0) /("tokens" -> "text")
        there was one(mockDocumentBackend).index(selection, PageRequest(1, 2), true)
      }

      "query for metadata when fields includes metadata" in new IndexFieldsScope {
        override lazy val request = fakeRequest("GET", "/?offset=1&limit=2")
        override val fields = "id,metadata"

        status(result) must beEqualTo(OK)
        contentAsString(result) must /("items") /#(0) /("metadata") /("foo" -> "bar")
        there was one(mockDocumentBackend).index(selection, PageRequest(1, 2), true)
      }

      "query for isFromOcr when fields includes isFromOcr" in new IndexFieldsScope {
        override lazy val request = fakeRequest("GET", "/?offset=1&limit=2")
        override val fields = "id,isFromOcr"

        status(result) must beEqualTo(OK)
        contentAsString(result) must /("items") /#(0) /("isFromOcr" -> false)
        there was one(mockDocumentBackend).index(selection, PageRequest(1, 2), false)
      }

      "ensure returned metadata matches the MetadataSchema" in new IndexFieldsScope {
        override lazy val documents = List(factory.document(metadataJson=Json.obj("foo" -> 3L, "baz" -> "baz")))
        override val fields = "id,metadata"

        val json = contentAsString(result)
        json must /("items") /#(0) /("metadata") /("foo" -> "3")
        json must not(contain("baz"))
      }

      "include text in JSON response" in new IndexFieldsScope {
        override val fields = "id,text"

        status(result) must beEqualTo(OK)
        val json = contentAsString(result)

        json must /("items") /#(0) /("text" -> "text")
      }

      "include tokens in JSON response" in new IndexFieldsScope {
        override val fields = "id,tokens"

        status(result) must beEqualTo(OK)
        val json = contentAsString(result)

        json must /("items") /#(0) /("tokens" -> "text")
      }

      "include metadata in JSON response" in new IndexFieldsScope {
        override val fields = "id,metadata"

        status(result) must beEqualTo(OK)
        val json = contentAsString(result)

        json must /("items") /#(0) /("metadata") /("foo" -> "bar")
      }

      "always return id, even if it is not in the fields" in new IndexFieldsScope {
        override val fields = "suppliedId"

        status(result) must beEqualTo(OK)
        val json = contentAsString(result)

        // specs2 doesn't have a Long JSON matcher
        //json must /("items") /#(0) /("id" -> documents(0).id)
        json must /("items") /#(0) /("suppliedId" -> "supplied 1")
        json must not(beMatching("url".r))
      }

      "ignore invalid fields" in new IndexFieldsScope {
        override val fields = "id,title,bleep"

        status(result) must beEqualTo(OK)
        val json = contentAsString(result)

        // specs2 doesn't have a Long JSON matcher
        //json must /("items") /#(0) /("id" -> documents(0).id)
        json must /("items") /#(0) /("title" -> "foo")
        json must not(beMatching("bleep".r))
      }

      "stream content" in new IndexFieldsScope {
        override lazy val request = fakeRequest("GET", "/?stream=true")

        status(result) must beEqualTo(OK)

        val json = contentAsString(result)
        json must /("pagination") /("total" -> 2)
        // specs2 doesn't have a Long JSON matcher
        //json must /("items") /#(0) /("id" -> documents(0).id)
      }

      "ensure returned metadata matches the MetadataSchema when streaming" in new IndexFieldsScope {
        override lazy val request = fakeRequest("GET", "/?stream=true")
        override lazy val documents = List(factory.document(metadataJson=Json.obj("foo" -> 3L, "baz" -> "baz")))
        override val fields = "id,metadata"

        val json = contentAsString(result)
        json must /("items") /#(0) /("metadata") /("foo" -> "3")
        json must not(contain("baz"))
      }
    }

    "#show" should {
      trait ShowScope extends BaseScope {
        val documentSetId = documentSet.id
        val documentId = 2L

        override def action = controller.show(documentSetId, documentId)
      }

      "return 404 when not found" in new ShowScope {
        mockDocumentBackend.show(documentSetId, documentId) returns Future.successful(None)
        status(result) must beEqualTo(NOT_FOUND)
        contentType(result) must beSome("application/json")
        val json = contentAsString(result)
        json must /("message" -> s"Document $documentId not found in document set $documentSetId")
      }

      "return JSON with status code 200" in new ShowScope {
        mockDocumentBackend.show(documentSetId, documentId) returns Future.successful(Some(factory.document(
          id=documentId,
          documentSetId=documentSetId,
          keywords=Seq("foo", "bar"),
          url=Some("http://example.org"),
          text="text",
          title="title",
          isFromOcr=false,
          suppliedId="suppliedId",
          metadataJson=Json.obj("foo" -> "bar")
        )))

        status(result) must beEqualTo(OK)
        contentType(result) must beSome("application/json")

        val json = contentAsString(result)

        // specs2 doesn't have a Long JSON matcher
        //json must /("id" -> documentId)
        json must not /("documentSetId")
        json must /("keywords") /#(0) /("foo")
        json must /("keywords") /#(1) /("bar")
        json must /("isFromOcr" -> false)
        json must /("url" -> "http://example.org")
        json must /("title" -> "title")
        json must /("text" -> "text")
        json must /("tokens" -> "text")
        json must /("metadata") /("foo" -> "bar")
        json must /("suppliedId" -> "suppliedId")
      }

      "ensure returned metadata matches the MetadataSchema" in new ShowScope {
        mockDocumentBackend.show(documentSetId, documentId) returns Future.successful(Some(factory.document(
          id=documentId,
          metadataJson=Json.obj("foo" -> 3L, "baz" -> "baz value")
        )))

        val json = contentAsString(result)
        json must /("metadata") /("foo" -> "3")
        json must not(contain("baz"))
      }
    }
  }
}
