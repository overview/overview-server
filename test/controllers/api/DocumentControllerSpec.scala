package controllers.api

import play.api.libs.json.Json
import scala.concurrent.Future

import controllers.backend.DocumentBackend
import org.overviewproject.tree.orm.Document // FIXME should be models.Document

class DocumentControllerSpec extends ApiControllerSpecification {
  trait BaseScope extends ApiControllerScope {
    val mockBackend = mock[DocumentBackend]
    val controller = new DocumentController {
      override val backend = mockBackend
    }
  }

  "DocumentController" should {
    "#index" should {
      trait IndexScope extends BaseScope {
        val documentSetId = 1L
        val q = ""

        override def action = controller.index(documentSetId, q)
      }

      "return JSON with status code 200" in new IndexScope {
        mockBackend.index(documentSetId, q) returns Future(Seq())
        status(result) must beEqualTo(OK)
        contentType(result) must beSome("application/json")
      }

      "return an empty Array when there are no Documents" in new IndexScope {
        mockBackend.index(documentSetId, q) returns Future(Seq())
        contentAsString(result) must beEqualTo("[]")
      }

      "return some Documents when there are Documents" in new IndexScope {
        val documents = Seq(
          factory.document(
            title="foo",
            keywords=Seq("foo", "bar"),
            suppliedId="supplied 1",
            text="text",
            url=Some("http://example.org")
          ),
          factory.document(title="", keywords=Seq(), suppliedId="", url=None)
        )
        mockBackend.index(documentSetId, q) returns Future(documents.map(_.toDocumentInfo))

        val json = contentAsString(result)

        json must /#(0) /("id" -> documents(0).id)
        json must /#(0) /("title" -> "foo")
        json must /#(0) /("keywords") /#(0) /("foo")
        json must /#(0) /("keywords") /#(1) /("bar")
        json must /#(0) /("suppliedId" -> "supplied 1")
        json must /#(0) /("url" -> "http://example.org")
        json must not /#(0) /("text")

        json must /#(1) /("id" -> documents(1).id)
        json must /#(1) /("title" -> "")
        // I can't get this one past specs2: json must /#(1) /("keywords" -> beEmpty[Seq[Any]])
        json must not /#(1) /("suppliedId")
        json must not /#(1) /("url")
        json must not /#(1) /("text")
      }
    }

    "#show" should {
      trait ShowScope extends BaseScope {
        val documentSetId = 1L
        val documentId = 2L

        override def action = controller.show(documentSetId, documentId)
      }

      "return 404 when not found" in new ShowScope {
        mockBackend.show(documentSetId, documentId) returns Future(None)
        status(result) must beEqualTo(NOT_FOUND)
        contentType(result) must beSome("application/json")
        val json = contentAsString(result)
        json must /("message" -> s"Document $documentId not found in document set $documentSetId")
      }

      "return JSON with status code 200" in new ShowScope {
        mockBackend.show(documentSetId, documentId) returns Future(Some(factory.document(
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
