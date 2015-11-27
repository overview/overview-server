package com.overviewdocs.documentcloud

import java.util.concurrent.TimeoutException
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.Future

import com.overviewdocs.http

class DocumentCloudServerSpec extends Specification with Mockito {
  trait BaseScope extends Scope {
    val mockHttpClient = smartMock[http.Client]
    val subject = new DocumentCloudServer {
      override protected val httpClient = mockHttpClient
    }

    def stubAnyResponse = {
      mockHttpClient.get(any)(any) returns Future.failed(new Exception("we don't care"))
    }
  }

  "#getIdList0" should {
    "fail with timeout" in new BaseScope {
      mockHttpClient.get(any)(any) returns Future.failed(new TimeoutException("No response received after 1234"))
      subject.getIdList0("foo", "", "") must beEqualTo(Left("Request to DocumentCloud timed out")).await
    }

    "fail with HTTP error code" in new BaseScope {
      mockHttpClient.get(any)(any) returns Future.successful(http.Response(403, Map(), "blah".getBytes("utf-8")))
      subject.getIdList0("foo", "", "") must beEqualTo(Left("DocumentCloud responded with HTTP 403 Forbidden")).await
    }

    "fail with invalid JSON" in new BaseScope {
      mockHttpClient.get(any)(any) returns Future.successful(http.Response(200, Map(), "{".getBytes("utf-8")))
      subject.getIdList0("foo", "", "") must beEqualTo(Left("DocumentCloud responded with invalid JSON")).await
    }

    "fail with parse error" in new BaseScope {
      mockHttpClient.get(any)(any) returns Future.successful(http.Response(200, Map(), "{}".getBytes("utf-8")))
      subject.getIdList0("foo", "", "") must beEqualTo(Left("Overview failed to parse DocumentCloud's JSON")).await
    }

    "succeed" in new BaseScope {
      mockHttpClient.get(any)(any) returns Future.successful(http.Response(200, Map(), """{
        "total": 5,
        "documents": [{
          "id": "123-foo",
          "title": "Foo Bar",
          "access": "public",
          "pages": 3,
          "resources": {
            "text": "https://assets.documentcloud.org/foo.txt",
            "page": {
              "text": "https://assets.documentcloud.org/foo-p{page}.txt"
            }
          }
        }]
      }""".getBytes("utf-8")))
      subject.getIdList0("foo", "", "") must beRight((IdList(Seq(
        IdListRow(
          "123-foo",
          "Foo Bar",
          3,
          "https://assets.documentcloud.org/foo.txt",
          "https://assets.documentcloud.org/foo-p{page}.txt",
          "public"
        )
      )), 5)).await
    }

    "request without auth" in new BaseScope {
      stubAnyResponse
      subject.getIdList0("foo", "", "")

      val captor = capture[http.Request]
      there was one(mockHttpClient).get(captor)(any)

      captor.value.maybeCredentials must beNone
    }

    "request with auth" in new BaseScope {
      stubAnyResponse
      subject.getIdList0("foo", "adam", "hooper")

      val captor = capture[http.Request]
      there was one(mockHttpClient).get(captor)(any)

      captor.value.maybeCredentials must beSome(http.Credentials("adam", "hooper"))
    }

    "escape the query" in new BaseScope {
      stubAnyResponse
      subject.getIdList0("foo+bar = baz", "", "")

      val captor = capture[http.Request]
      there was one(mockHttpClient).get(captor)(any)

      captor.value.url must beEqualTo("https://www.documentcloud.org/api/search.json?q=foo%2Bbar+%3D+baz&page=1&per_page=1000")
    }
  }

  "#getIdList" should {
    "add a (base-0) page to the query" in new BaseScope {
      stubAnyResponse
      subject.getIdList("foo+bar = baz", "", "", 3)

      val captor = capture[http.Request]
      there was one(mockHttpClient).get(captor)(any)

      captor.value.url must beEqualTo("https://www.documentcloud.org/api/search.json?q=foo%2Bbar+%3D+baz&page=4&per_page=1000")
    }
  }

  "#getText" should {
    "fail with timeout" in new BaseScope {
      mockHttpClient.get(any)(any) returns Future.failed(new TimeoutException("No response received after 1234"))
      subject.getText("http://foo", "", "", "public") must beLeft("Request to DocumentCloud timed out").await
    }

    "fail with HTTP error" in new BaseScope {
      mockHttpClient.get(any)(any) returns Future.successful(http.Response(403, Map(), "blah".getBytes("utf-8")))
      subject.getText("http://foo", "", "", "public") must beEqualTo(Left("DocumentCloud responded with HTTP 403 Forbidden")).await
    }

    "parse UTF-8 and strip invalid characters" in new BaseScope {
      // basically, we're testing that we call Textify()
      mockHttpClient.get(any)(any) returns Future.successful(http.Response(200, Map(), Array(0x1f, 'f', 0xc3, 0xa7).map(_.toByte)))
      subject.getText("http://foo", "", "", "public") must beEqualTo(Right(" fç")).await
    }

    "request a public document" in new BaseScope {
      stubAnyResponse
      subject.getText("http://foo", "", "", "public")

      val captor = capture[http.Request]
      there was one(mockHttpClient).get(captor)(any)

      captor.value.followRedirects must beFalse
    }

    "request a private document" in new BaseScope {
      stubAnyResponse
      subject.getText("http://foo", "", "", "private")

      val captor = capture[http.Request]
      there was one(mockHttpClient).get(captor)(any)

      captor.value.followRedirects must beTrue
    }
  }
}
