package controllers.forms

import play.api.libs.json.{JsObject,Json}

import org.specs2.mutable.Specification

class OverviewFormatsSpec extends Specification {
  "metadataJson" should {
    def bind(value: String): Either[String,JsObject] = {
      OverviewFormats.metadataJson.bind("value", Map("value" -> value))
        .left.map(_.head.message)
    }

    def unbind(value: JsObject): String = {
      OverviewFormats.metadataJson.unbind("value", value)("value")
    }

    "bind {}" in {
      bind("{}") must beRight(Json.obj())
    }

    "bind JSON" in {
      bind("""{"foo":"bar","baz":["moo","mar",{"yes":true}]}""") must beRight(Json.obj(
        "foo" -> "bar",
        "baz" -> Json.arr("moo", "mar", Json.obj("yes" -> true))
      ))
    }

    "fail to bind an empty string" in {
      bind("") must beLeft("error.invalidMetadataJson")
    }

    "fail to bind invalid JSON" in {
      bind("{") must beLeft("error.invalidMetadataJson")
    }

    "fail to bind non-Object JSON" in {
      bind("""["foo", "bar"]""") must beLeft("error.invalidMetadataJson")
    }

    "unbind correctly" in {
      unbind(Json.obj("foo" -> "bar")) must beEqualTo("""{"foo":"bar"}""")
    }
  }
}
