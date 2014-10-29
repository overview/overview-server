package controllers

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.mvc.{Request,RequestHeader}
import play.api.test.FakeRequest

import models.pagination.PageRequest
import models.SelectionRequest

class ControllerSpec extends Specification {
  "pageRequest" should {
    trait PageScope extends Scope {
      trait F {
        def f(request: RequestHeader, maxLimit: Int): PageRequest
      }
      val controller = new Controller with F {
        // Make it public
        override def f(request: RequestHeader, maxLimit: Int) = pageRequest(request, maxLimit)
      }
    }

    "default to offset 0" in new PageScope {
      controller.f(FakeRequest(), 1000).offset must beEqualTo(0)
    }

    "let you specify an offset" in new PageScope {
      controller.f(FakeRequest("GET", "/?offset=123"), 1000).offset must beEqualTo(123)
    }

    "ignore offsets that cannot be parsed" in new PageScope {
      controller.f(FakeRequest("GET", "/?offset=123foo"), 1000).offset must beEqualTo(0)
    }

    "ignore offsets that overflow" in new PageScope {
      controller.f(FakeRequest("GET", "/?offset=999999999999999999"), 1000).offset must beEqualTo(0)
    }

    "ignore negative offsets" in new PageScope {
      controller.f(FakeRequest("GET", "/?offset=-123"), 1000).offset must beEqualTo(0)
    }

    "default to a limit of maxLimit" in new PageScope {
      controller.f(FakeRequest(), 1000).limit must beEqualTo(1000)
    }

    "let you specify a limit" in new PageScope {
      controller.f(FakeRequest(), 100).limit must beEqualTo(100)
    }

    "ignore limits that cannot be parsed" in new PageScope {
      controller.f(FakeRequest("GET", "/?limit=10foo"), 1000).limit must beEqualTo(1000)
    }

    "ignore limits that are higher than the maximum" in new PageScope {
      controller.f(FakeRequest("GET", "/?limit=9999"), 1000).limit must beEqualTo(1000)
    }

    "ignore negative limits" in new PageScope {
      controller.f(FakeRequest("GET", "/?limit=-1"), 1000).limit must beEqualTo(1000)
    }

    "change limit 0 to 1" in new PageScope {
      controller.f(FakeRequest("GET", "/?limit=0"), 1000).limit must beEqualTo(1)
    }

    "parse both offset and limit in the same request" in new PageScope {
      controller.f(FakeRequest("GET", "/?offset=20&limit=30"), 1000) must beEqualTo(PageRequest(20, 30))
    }
  }

  "selectionRequest" should {
    trait SelectionScope extends Scope {
      trait F {
        def f(documentSetId: Long, request: Request[_]): SelectionRequest
      }
      val controller = new Controller with F {
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

      def test(queryString: String, expect: SelectionRequest) = {
        f(1L, queryString) must beEqualTo(expect)
      }
      def test(data: Seq[(String,String)], expect: SelectionRequest) = {
        f(1L, data: _*) must beEqualTo(expect)
      }
    }

    "with GET parameters" should {
      "default to all documents in the document set" in new SelectionScope {
        test("", SelectionRequest(1L))
      }

      "make a SelectionRequest with documents" in new SelectionScope {
        test("/?documents=1,2,3", SelectionRequest(1L, documentIds=Seq(1L, 2L, 3L)))
      }

      "make a SelectionRequest with nodes" in new SelectionScope {
        test("/?nodes=2,3,4", SelectionRequest(1L, nodeIds=Seq(2L, 3L, 4L)))
      }

      "make a SelectionRequest with tags" in new SelectionScope {
        test("/?tags=3,4,5", SelectionRequest(1L, tagIds=Seq(3L, 4L, 5L)))
      }

      "make a SelectionRequest with searchResults" in new SelectionScope {
        test("/?searchResults=4,5,6", SelectionRequest(1L, searchResultIds=Seq(4L, 5L, 6L)))
      }

      "make a SelectionRequest with storeObjects" in new SelectionScope {
        test("/?objects=5,6,7", SelectionRequest(1L, storeObjectIds=Seq(5L, 6L, 7L)))
      }

      "make a SelectionRequest with untagged, the deprecated way" in new SelectionScope {
        test("/?tags=0", SelectionRequest(1L, tagIds=Seq(), tagged=Some(false)))
      }

      "make a SelectionRequest with untagged, the good way" in new SelectionScope {
        test("/?tagged=false", SelectionRequest(1L, tagged=Some(false)))
      }

      "make a SelectionRequest with q" in new SelectionScope {
        test("/?q=foo", SelectionRequest(1L, q="foo"))
      }
    }

    "with POST parameters" should {
      "default to all documents in the document set" in new SelectionScope {
        test(Seq(), SelectionRequest(1L))
      }

      "make a SelectionRequest with documents" in new SelectionScope {
        test(Seq("documents" -> "1,2,3"), SelectionRequest(1L, documentIds=Seq(1L, 2L, 3L)))
      }

      "make a SelectionRequest with nodes" in new SelectionScope {
        test(Seq("nodes" -> "2,3,4"), SelectionRequest(1L, nodeIds=Seq(2L, 3L, 4L)))
      }

      "make a SelectionRequest with tags" in new SelectionScope {
        test(Seq("tags" -> "3,4,5"), SelectionRequest(1L, tagIds=Seq(3L, 4L, 5L)))
      }

      "make a SelectionRequest with searchResults" in new SelectionScope {
        test(Seq("searchResults" -> "4,5,6"), SelectionRequest(1L, searchResultIds=Seq(4L, 5L, 6L)))
      }

      "make a SelectionRequest with storeObjects" in new SelectionScope {
        test(Seq("objects" -> "5,6,7"), SelectionRequest(1L, storeObjectIds=Seq(5L, 6L, 7L)))
      }

      "make a SelectionRequest with untagged, the deprecated way" in new SelectionScope {
        test(Seq("tags" -> "0"), SelectionRequest(1L, tagIds=Seq(), tagged=Some(false)))
      }

      "make a SelectionRequest with untagged, the good way" in new SelectionScope {
        test(Seq("tagged" -> "false"), SelectionRequest(1L, tagged=Some(false)))
      }

      "make a SelectionRequest with q" in new SelectionScope {
        test(Seq("q" -> "foo"), SelectionRequest(1L, q="foo"))
      }
    }

    "prefer POST parameters over GET parameters" in new SelectionScope {
      val request = FakeRequest("GET", "/?q=foo").withFormUrlEncodedBody("q" -> "bar")
      controller.f(1L, request) must beEqualTo(SelectionRequest(1L, q="bar"))
    }

    "allow some GET parameters and other POST parameters" in new SelectionScope {
      val request = FakeRequest("GET", "/?nodes=1").withFormUrlEncodedBody("q" -> "bar")
      controller.f(1L, request) must beEqualTo(SelectionRequest(1L, nodeIds=Seq(1L), q="bar"))
    }
  }
}
