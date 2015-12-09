package com.overviewdocs.metadata

import org.specs2.mutable.Specification
import play.api.libs.json.{JsObject,Json}
import play.api.libs.iteratee.{Enumerator,Iteratee}
import scala.concurrent.ExecutionContext.Implicits.global

import com.overviewdocs.util.AwaitMethod

class MetadataAnalyzerSpec extends Specification with AwaitMethod {
  "#uniqueFieldNames" should {
    def go(inputMetadataJsons: JsObject*): Seq[String] = await {
      Enumerator(inputMetadataJsons: _*)
        .through(MetadataAnalyzer.uniqueFieldNames)
        .run(Iteratee.getChunks)
    }

    "list one JsObject's field names" in {
      go(Json.obj("foo" -> "bar", "baz" -> 3)) must beEqualTo(Seq("foo", "baz"))
    }

    "remove duplicates" in {
      go(Json.obj("foo" -> "bar"), Json.obj("foo" -> "bar")) must beEqualTo(Seq("foo"))
    }

    "remove multiple duplicates" in {
      go(
        Json.obj("foo" -> "bar", "bar" -> "baz"),
        Json.obj("bar" -> "baz", "baz" -> "foo", "foo" -> "meep")
      ) must beEqualTo(Seq("foo", "bar", "baz"))
    }

    "return each unique entry" in {
      go(Json.obj("foo" -> "bar"), Json.obj("bar" -> "baz")) must beEqualTo(Seq("foo", "bar"))
    }

    "work with three inputs" in {
      go(
        Json.obj("foo" -> "bar", "bar" -> "baz"),
        Json.obj("bar" -> "foo", "baz" -> "bar"),
        Json.obj("fee" -> "mee")
      ) must beEqualTo(Seq("foo", "bar", "baz", "fee"))
    }
  }
}
