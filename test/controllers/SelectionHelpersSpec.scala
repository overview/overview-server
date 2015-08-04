package controllers

import java.util.UUID
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.mvc.{Request,Result}
import play.api.test.FakeRequest
import scala.concurrent.Future

import controllers.backend.SelectionBackend
import models.{InMemorySelection,Selection,SelectionRequest}
import com.overviewdocs.query.{Field,PhraseQuery}
import test.helpers.MockMessagesApi

class SelectionHelpersSpec extends Specification with Mockito {
  "selectionRequest" should {
    trait SelectionScope extends Scope {
      trait F {
        def f(documentSetId: Long, request: Request[_]): Either[Result,SelectionRequest]
      }
      val controller = new Controller with SelectionHelpers with F with TestController {
        override def messagesApi = new MockMessagesApi()
        override def f(documentSetId: Long, request: Request[_]) = selectionRequest(documentSetId, request)
      }
      def f(documentSetId: Long, path: String) = {
        val request = FakeRequest("GET", path)
        controller.f(documentSetId, request)
      }
      def f(documentSetId: Long, data: (String,String)*) = {
        val request = FakeRequest("POST", "").withFormUrlEncodedBody(data: _*)
        controller.f(documentSetId, request)
      }

      def test(queryString: String, expect: Either[Result,SelectionRequest]) = {
        f(1L, queryString) must beEqualTo(expect)
      }
      def test(data: Seq[(String,String)], expect: Either[Result,SelectionRequest]) = {
        f(1L, data: _*) must beEqualTo(expect)
      }
    }

    "with GET parameters" should {
      "default to all documents in the document set" in new SelectionScope {
        test("", Right(SelectionRequest(1L)))
      }

      "make a SelectionRequest with documents" in new SelectionScope {
        test("/?documents=1,2,3", Right(SelectionRequest(1L, documentIds=Seq(1L, 2L, 3L))))
      }

      "make a SelectionRequest with nodes" in new SelectionScope {
        test("/?nodes=2,3,4", Right(SelectionRequest(1L, nodeIds=Seq(2L, 3L, 4L))))
      }

      "make a SelectionRequest with tags" in new SelectionScope {
        test("/?tags=3,4,5", Right(SelectionRequest(1L, tagIds=Seq(3L, 4L, 5L))))
      }

      "make a SelectionRequest with storeObjects" in new SelectionScope {
        test("/?objects=5,6,7", Right(SelectionRequest(1L, storeObjectIds=Seq(5L, 6L, 7L))))
      }

      "make a SelectionRequest with untagged" in new SelectionScope {
        test("/?tagged=false", Right(SelectionRequest(1L, tagged=Some(false))))
      }

      "make a SelectionRequest with q" in new SelectionScope {
        test("/?q=foo", Right(SelectionRequest(1L, q=Some(PhraseQuery(Field.All, "foo")))))
      }

      "return a BadRequest on query syntax error" in new SelectionScope {
        f(1L, "/?q=(foo+AND") must beLeft { (result: Result) =>
          result.header.status must beEqualTo(400)
          result.header.headers.get(CONTENT_TYPE) must beSome("application/json")
        }
      }
    }

    "with POST parameters" should {
      "default to all documents in the document set" in new SelectionScope {
        test(Seq(), Right(SelectionRequest(1L)))
      }

      "make a SelectionRequest with documents" in new SelectionScope {
        test(Seq("documents" -> "1,2,3"), Right(SelectionRequest(1L, documentIds=Seq(1L, 2L, 3L))))
      }

      "make a SelectionRequest with nodes" in new SelectionScope {
        test(Seq("nodes" -> "2,3,4"), Right(SelectionRequest(1L, nodeIds=Seq(2L, 3L, 4L))))
      }

      "make a SelectionRequest with tags" in new SelectionScope {
        test(Seq("tags" -> "3,4,5"), Right(SelectionRequest(1L, tagIds=Seq(3L, 4L, 5L))))
      }

      "make a SelectionRequest with storeObjects" in new SelectionScope {
        test(Seq("objects" -> "5,6,7"), Right(SelectionRequest(1L, storeObjectIds=Seq(5L, 6L, 7L))))
      }

      "make a SelectionRequest with untagged" in new SelectionScope {
        test(Seq("tagged" -> "false"), Right(SelectionRequest(1L, tagged=Some(false))))
      }

      "make a SelectionRequest with q" in new SelectionScope {
        test(Seq("q" -> "foo"), Right(SelectionRequest(1L, q=Some(PhraseQuery(Field.All, "foo")))))
      }
    }

    "prefer POST parameters over GET parameters" in new SelectionScope {
      val request = FakeRequest("GET", "/?q=foo").withFormUrlEncodedBody("q" -> "bar")
      controller.f(1L, request) must beEqualTo(Right(SelectionRequest(1L, q=Some(PhraseQuery(Field.All, "bar")))))
    }

    "allow some GET parameters and other POST parameters" in new SelectionScope {
      val request = FakeRequest("GET", "/?nodes=1").withFormUrlEncodedBody("q" -> "bar")
      controller.f(1L, request) must beEqualTo(Right(SelectionRequest(1L, nodeIds=Seq(1L), q=Some(PhraseQuery(Field.All, "bar")))))
    }
  }

  "requestToSelection" should {
    trait RequestToSelectionScope extends Scope {
      // This implicitly tests selectionRequest(). Meh.
      val documentSetId: Long = 123L
      val userEmail: String = "user@example.org"
      val selection = InMemorySelection(Seq(1L, 2L, 3L))
      val selectionId = "9cf4d95a-39bd-4463-85a3-cac272a20bc2"
      val mockSelectionBackend = smartMock[SelectionBackend]

      class AController extends Controller with TestController with SelectionHelpers {
        override val selectionBackend = mockSelectionBackend
        def go(request: Request[_]) = requestToSelection(documentSetId, userEmail, request)
      }
      val controller = new AController()
    }

    "use SelectionBackend#find() if selectionId is set" in new RequestToSelectionScope {
      mockSelectionBackend.find(any, any) returns Future.successful(Some(selection))
      val request = FakeRequest("POST", "").withFormUrlEncodedBody("selectionId" -> selectionId)
      controller.go(request) must beEqualTo(Right(selection)).await
      there was one(mockSelectionBackend).find(documentSetId, UUID.fromString(selectionId))
    }

    "return NotFound if selectionId is set and invalid" in new RequestToSelectionScope {
      mockSelectionBackend.find(any, any) returns Future.successful(None)
      val request = FakeRequest("POST", "").withFormUrlEncodedBody("selectionId" -> selectionId)
      controller.go(request).map(_.left.map(_.header.status)) must beLeft(404).await
    }

    "uses SelectionBackend#create() if refresh=true" in new RequestToSelectionScope {
      val selectionRequest = SelectionRequest(documentSetId, Seq(), Seq(), Seq(), Seq(), None, Some(PhraseQuery(Field.All, "foo")))
      mockSelectionBackend.create(any, any) returns Future.successful(selection)
      val request = FakeRequest("POST", "").withFormUrlEncodedBody("q" -> "foo", "refresh" -> "true")
      controller.go(request) must beEqualTo(Right(selection)).await
      there was one(mockSelectionBackend).create(userEmail, selectionRequest)
    }

    "uses SelectionBackend#findOrCreate() as a fallback" in new RequestToSelectionScope {
      val selectionRequest = SelectionRequest(documentSetId, Seq(), Seq(), Seq(), Seq(), None, Some(PhraseQuery(Field.All, "foo")))
      mockSelectionBackend.findOrCreate(any, any, any) returns Future.successful(selection)
      val request = FakeRequest("POST", "").withFormUrlEncodedBody("q" -> "foo")
      controller.go(request) must beEqualTo(Right(selection)).await
      there was one(mockSelectionBackend).findOrCreate(userEmail, selectionRequest, None)
    }

    "return BadRequest on invalid search phrase" in new RequestToSelectionScope {
      val request = FakeRequest("POST", "").withFormUrlEncodedBody("q" -> "(foo AND")
      controller.go(request).map(_.left.map(_.header.status)) must beLeft(400).await
    }
  }
}
