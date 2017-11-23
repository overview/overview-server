package controllers

import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json
import play.api.mvc.{RequestHeader}
import play.api.test.FakeRequest

import models.pagination.PageRequest

class ControllerHelpersSpec extends Specification with JsonMatchers {
  "jsonError" should {
    "generate a JSON Error object" in {
      trait MyTest {
        self: ControllerHelpers =>

        val err = jsonError("aaa", "foo")
      }

      val controller = new ControllerHelpers with MyTest

      Json.stringify(controller.err) must /("message" -> "foo")
      Json.stringify(controller.err) must /("code" -> "aaa")
    }
  }

  "pageRequest" should {
    trait PageScope extends Scope {
      trait F {
        def f(request: RequestHeader, maxLimit: Int): PageRequest
      }
      val controller = new ControllerHelpers with F {
        // Make it public
        override def f(request: RequestHeader, maxLimit: Int) = pageRequest(request, maxLimit)
      }
    }

    "default to offset 0" in new PageScope {
      controller.f(FakeRequest(), 1000).offset must beEqualTo(0)
    }

    "default to reverse=false" in new PageScope {
      controller.f(FakeRequest(), 1000).reverse must beEqualTo(false)
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

    "let you set reverse=true" in new PageScope {
      controller.f(FakeRequest("GET", "/?reverse=true"), 1000).reverse must beEqualTo(true)
    }

    "let you set reverse=false" in new PageScope {
      controller.f(FakeRequest("GET", "/?reverse=false"), 1000).reverse must beEqualTo(false)
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
      controller.f(FakeRequest("GET", "/?offset=20&limit=30"), 1000) must beEqualTo(PageRequest(20, 30, false))
    }
  }

  "RequestData" should {
    trait RequestDataScope extends Scope {
      import play.api.mvc.Request
      class MyController extends ControllerHelpers {
        def f(request: Request[_]) = RequestData(request)
      }
      def qs(s: String) = {
        val controller = new MyController
        controller.f(FakeRequest("GET", "/?" + s))
      }
    }

    "get an immutable.Seq[Long]" in new RequestDataScope {
      qs("ids=1,2,3,4").getLongs("ids") must beEqualTo(Vector(1L, 2L, 3L, 4L))
    }

    "#get2LevelStringMap" should {
      "get a 2-level Map" in new RequestDataScope {
        qs("x.foo.bar=baz&x.foo.moo=mar&x.bar.foo=moo").get2LevelStringMap("x") must beEqualTo(Map(
          "foo" -> Map("bar" -> "baz", "moo" -> "mar"),
          "bar" -> Map("foo" -> "moo")
        ))
      }

      "ignore roots that are not requested" in new RequestDataScope {
        qs("x.foo.bar=baz&y.foo.moo=mar&x.bar.foo=moo").get2LevelStringMap("x") must beEqualTo(Map(
          "foo" -> Map("bar" -> "baz"),
          "bar" -> Map("foo" -> "moo")
        ))
      }

      "ignore extra dots" in new RequestDataScope {
        qs("x.foo.bar.baz=moo").get2LevelStringMap("x") must beEqualTo(Map(
          "foo" -> Map("bar.baz" -> "moo"),
        ))
      }

      "return empty-String values" in new RequestDataScope {
        qs("x.foo.bar=").get2LevelStringMap("x") must beEqualTo(Map(
          "foo" -> Map("bar" -> ""),
        ))
      }
    }

    "#getBase64BitSet" should {
      "get a BitSet" in new RequestDataScope {
        // 1, 3, 5, 11:
        // 0b0101010000010... in a bitset
        // 0b010101: V (0x15)       first base64 character
        //       0b000001: B (0x1)  second base64 character
        qs("x=VBA").getBase64BitSet("x").map(_.toVector) must beSome(Vector(1, 3, 5, 11))
      }

      "read numbers >63 (that is, multi-word bitsets)" in new RequestDataScope {
        // 2, 66, 67:
        // 0b001000...110000
        // 0b001000: I (that's bytes 0-5)
        // bytes 6-11, 12-17, ..., 60-65 will all be empty
        // 0b110000: w
        qs("x=IAAAAAAAAAAwAAA").getBase64BitSet("x").map(_.toVector) must beSome(Vector(2, 66, 67))
      }

      "fail on invalid Base64" in new RequestDataScope {
        qs("x=ab,c").getBase64BitSet("x") must beNone
      }

      "allow missing equals signs at the end of the input" in new RequestDataScope {
        qs("x=QA").getBase64BitSet("x").map(_.toVector) must beSome(Vector(1))
      }

      "allow equals signs at the end of the input" in new RequestDataScope {
        qs("x=QA%3D%3D").getBase64BitSet("x").map(_.toVector) must beSome(Vector(1))
      }

      "allow + and /" in new RequestDataScope {
        // + is 62, / is 63
        // 0b111110111111
        qs("x=%2B%2FAA").getBase64BitSet("x").map(_.toVector) must beSome(Vector(0, 1, 2, 3, 4, 6, 7, 8, 9, 10, 11))
      }

      "allow URL-safe - and _ as replacements for + and /" in new RequestDataScope {
        qs("x=-_AA").getBase64BitSet("x").map(_.toVector) must beSome(Vector(0, 1, 2, 3, 4, 6, 7, 8, 9, 10, 11))
      }

      "allow empty string as empty bitset" in new RequestDataScope {
        qs("x=").getBase64BitSet("x").map(_.toVector) must beSome(Vector.empty[Int])
      }
    }
  }
}
