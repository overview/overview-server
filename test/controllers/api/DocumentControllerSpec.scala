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
            title=Some("foo"),
            description="foo bar",
            suppliedId=Some("supplied 1"),
            text=Some("text"),
            url=Some("http://example.org")
          ),
          factory.document(title=None, description="", suppliedId=None, text=None, url=None)
        )
        mockBackend.index(documentSetId, q) returns Future(documents)

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
  }
}
