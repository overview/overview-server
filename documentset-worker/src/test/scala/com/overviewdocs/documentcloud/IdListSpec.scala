package com.overviewdocs.documentcloud

import org.specs2.mutable.Specification
import play.api.libs.json.Json

class IdListSpec extends Specification {
  "IdList" should {
    "encode as a String" in {
      IdList.encode(IdList(Seq(
        IdListRow("123-foo-bar", "Foo Bar", 3, "http://some-big-url/123-foo-bar.txt", "http://moo", "public"),
        IdListRow("234-bar-foo", "Bar Foo", 2, "http://some-big-url/234-bar-foo.txt", "http://mar", "private")
      ))) must beEqualTo("""
        123-foo-bar\u001fFoo Bar\u001f3\u001fhttp://some-big-url/123-foo-bar.txt\u001fhttp://moo\u001fpublic
        234-bar-foo\u001fBar Foo\u001f2\u001fhttp://some-big-url/234-bar-foo.txt\u001fhttp://mar\u001fprivate
      """.trim.replaceAll("\n *", "\u001e"))
    }

    "decode from a String" in {
      IdList.decode("""
        123-foo-bar\u001fFoo Bar\u001f3\u001fhttp://some-big-url/123-foo-bar.txt\u001fhttp://moo\u001fpublic
        234-bar-foo\u001fBar Foo\u001f2\u001fhttp://some-big-url/234-bar-foo.txt\u001fhttp://mar\u001fprivate
      """.trim.replaceAll("\n *", "\u001e")) must beSome(IdList(Seq(
        IdListRow("123-foo-bar", "Foo Bar", 3, "http://some-big-url/123-foo-bar.txt", "http://moo", "public"),
        IdListRow("234-bar-foo", "Bar Foo", 2, "http://some-big-url/234-bar-foo.txt", "http://mar", "private")
      )))
    }

    "fail to decode a malformed String" in {
      IdList.decode("""
        123-foo-bar\u001fFoo Bar\u001f3\u001fhttp://some-big-url/123-foo-bar.txt\u001fhttp://moo
        234-bar-foo\u001fBar Foo\u001f2\u001fhttp://some-big-url/234-bar-foo.txt
      """.trim.replaceAll("\n *", "\u001e")) must beNone
    }

    "parse valid JSON" in {
      IdList.parseDocumentCloudSearchResult(Json.obj(
        "total" -> 5,
        "documents" -> Json.arr(
          Json.obj(
            "id" -> "123-foo-bar",
            "title" -> "Foo Bar",
            "access" -> "public",
            "pages" -> 5,
            "resources" -> Json.obj(
              "text" -> "https://assets.documentcloud.org/a.txt",
              "page" -> Json.obj(
                "text" -> "https://assets.documentcloud.org/a-p{page}.txt"
              )
            )
          ),
          Json.obj(
            "id" -> "234-bar-foo",
            "title" -> "Bar Foo",
            "access" -> "private",
            "pages" -> 3,
            "resources" -> Json.obj(
              "text" -> "https://www.documentcloud.org/b.txt",
              "page" -> Json.obj(
                "text" -> "https://www.documentcloud.org/b-p{page}.txt"
              )
            )
          )
        )
      )) must beSome((IdList(Seq(
        IdListRow(
          "123-foo-bar",
          "Foo Bar",
          5,
          "https://assets.documentcloud.org/a.txt",
          "https://assets.documentcloud.org/a-p{page}.txt",
          "public"
        ),
        IdListRow(
          "234-bar-foo",
          "Bar Foo",
          3,
          "https://www.documentcloud.org/b.txt",
          "https://www.documentcloud.org/b-p{page}.txt",
          "private"
        )
      )), 5))
    }

    "fail to parse JSON that is missing something" in {
      IdList.parseDocumentCloudSearchResult(Json.obj()) must beNone
    }
  }
}
