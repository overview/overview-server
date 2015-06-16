package org.overviewproject.metadata

import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json

class MetadataSpec extends Specification {
  trait BaseScope extends Scope {
    val schema = MetadataSchema(1, Seq(MetadataField("foo", MetadataFieldType.String)))
    val metadata = Metadata(schema)
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
      metadata.getString("foo") must beEqualTo("")
    }

    "return the value" in new BaseScope {
      override val metadata = Metadata(schema, Json.obj("foo" -> "bar"))
      metadata.getString("foo") must beEqualTo("bar")
    }

    "return empty string when set to a non-JsString" in new BaseScope {
      override val metadata = Metadata(schema, Json.obj("foo" -> 2))
      metadata.getString("foo") must beEqualTo("")
    }

    "throw IllegalArgumentException when the field is not in the schema" in new BaseScope {
      metadata.getString("foo2") must throwA(
        new IllegalArgumentException("The metadata schema does not include a `foo2` of type `String`")
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
}
