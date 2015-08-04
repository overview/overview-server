package com.overviewdocs.metadata

import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.{JsArray,JsBoolean,JsNull,JsObject,JsNumber,JsString,JsValue,Json}

class MetadataSpec extends Specification {
  trait BaseScope extends Scope {
    val schema = MetadataSchema(1, Seq(MetadataField("foo", MetadataFieldType.String)))
    val json: JsObject = Json.obj()
    lazy val metadata = Metadata(schema, json)
  }

  "setString" should {
    "maintain the same schema" in new BaseScope {
      metadata.setString("foo", "bar").schema must beEqualTo(schema)
    }

    "set a String value" in new BaseScope {
      metadata.setString("foo", "bar").json must beEqualTo(Json.obj("foo" -> "bar"))
    }

    "throw IllegalArgumentException when the field is not in the schema" in new BaseScope {
      metadata.setString("foo2", "bar") must throwA(
        new IllegalArgumentException("The metadata schema does not include a `foo2` of type `String`")
      )
    }
  }

  "getString" should {
    "return empty string when unset" in new BaseScope {
      override val json = Json.obj()
      metadata.getString("foo") must beEqualTo("")
    }

    "return the value" in new BaseScope {
      override val json = Json.obj("foo" -> "bar")
      metadata.getString("foo") must beEqualTo("bar")
    }

    trait CastStringScope extends Scope {
      def test(jsValue: JsValue, output: String) = {
        val schema = MetadataSchema(1, Seq(MetadataField("foo", MetadataFieldType.String)))
        lazy val metadata = Metadata(schema, Json.obj("foo" -> jsValue))
        metadata.getString("foo") must beEqualTo(output)
      }
    }

    "cast Number->String" in new CastStringScope { test(JsNumber(3.14), "3.14") }
    "cast true->String" in new CastStringScope { test(JsBoolean(true), "true") }
    "cast false->String" in new CastStringScope { test(JsBoolean(false), "false") }
    "cast Array->String" in new CastStringScope { test(Json.arr("bar", 3, "baz"), """["bar",3,"baz"]""") } // arbitrary
    "cast Object->String" in new CastStringScope { test(Json.obj("bar" -> "baz"), """{"bar":"baz"}""") } // arbitrary
    "cast null->String" in new CastStringScope { test(JsNull, "") }

    "throw IllegalArgumentException when the field is not in the schema" in new BaseScope {
      metadata.getString("foo2") must throwA(
        new IllegalArgumentException("The metadata schema does not include a `foo2`")
      )
    }
  }

  "json" should {
    "not change after setting" in new BaseScope {
      val json1 = metadata.json
      metadata.setString("foo", "bar")
      json1 must beEqualTo(Json.obj())
    }
  }

  "cleanJson" should {
    "remove an errant field" in new BaseScope {
      val metadata2 = Metadata(schema, Json.obj("foo" -> "bar", "bar" -> "baz"))
      metadata2.cleanJson must beEqualTo(Json.obj("foo" -> "bar"))
    }

    "add a missing String" in new BaseScope {
      val metadata2 = Metadata(schema, Json.obj())
      metadata2.cleanJson must beEqualTo(Json.obj("foo" -> ""))
    }

    "convert a Number to a String" in new BaseScope {
      val metadata2 = Metadata(schema, Json.obj("foo" -> 3L))
      metadata2.cleanJson must beEqualTo(Json.obj("foo" -> "3"))
    }
  }
}
