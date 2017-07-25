package com.overviewdocs.metadata

import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.{JsObject,Json}

class MetadataSchemaSpec extends Specification with JsonMatchers {
  trait BaseScope extends Scope

  "#toJson" should {
    trait ToJsonScope extends BaseScope {
      val version: Int = 1
      val fields: Seq[MetadataField] = Seq()
      def json: String = MetadataSchema(version, fields).toJson.toString
    }

    "include a version" in new ToJsonScope {
      json must /("version" -> 1)
    }

    "include a String field" in new ToJsonScope {
      override val fields = Seq(MetadataField("foo", MetadataFieldType.String, MetadataFieldDisplay.TextInput))
      json must /("fields") /#(0) /("name" -> "foo")
      json must /("fields") /#(0) /("type" -> "String")
      json must /("fields") /#(0) /("display" -> "TextInput")
    }
  }

  "::fromJson" should {
    trait FromJsonScope extends BaseScope {
      def from(json: String) = MetadataSchema.fromJson(Json.parse(json))
    }

    "parse the version" in new FromJsonScope {
      from("""{"version": 1,"fields":[]}""").version must beEqualTo(1)
    }

    "fail on too-high version" in new FromJsonScope {
      from("""{"version":2,"fields":[]}""") must throwA[IllegalArgumentException]
    }

    "fail when version is missing" in new FromJsonScope {
      from("""{"fields":[{"name":"foo","type":"String"}]}""") must throwA[IllegalArgumentException]
    }

    "parse a String field" in new FromJsonScope {
      from("""{"version":1,"fields":[{"name":"foo","type":"String","display":"TextInput"}]}""").fields must beEqualTo(Seq(
        MetadataField("foo", MetadataFieldType.String, MetadataFieldDisplay.TextInput)
      ))
    }

    "parse a Div field" in new FromJsonScope {
      from("""{"version":1,"fields":[{"name":"foo","type":"String","display":"Div"}]}""").fields must beEqualTo(Seq(
        MetadataField("foo", MetadataFieldType.String, MetadataFieldDisplay.Div)
      ))
    }

    "parse a Pre field" in new FromJsonScope {
      from("""{"version":1,"fields":[{"name":"foo","type":"String","display":"Pre"}]}""").fields must beEqualTo(Seq(
        MetadataField("foo", MetadataFieldType.String, MetadataFieldDisplay.Pre)
      ))
    }

    "default to String+TextInput" in new FromJsonScope {
      from("""{"version":1,"fields":[{"name":"foo"}]}""").fields must beEqualTo(List(
        MetadataField("foo", MetadataFieldType.String, MetadataFieldDisplay.TextInput)
      ))
    }

    "parse fields in order" in new FromJsonScope {
      from("""{"version":1,"fields":[{"name":"foo","type":"String","display":"TextInput"},{"name":"bar","type":"String","display":"TextInput"}]}""").fields must beEqualTo(Seq(
        MetadataField("foo", MetadataFieldType.String, MetadataFieldDisplay.TextInput),
        MetadataField("bar", MetadataFieldType.String, MetadataFieldDisplay.TextInput)
      ))
    }

    "not parse an invalid type" in new FromJsonScope {
      from("""{"version":1,"fields":[{"name":"foo","type":"string"}]}""") must throwA[IllegalArgumentException]
    }

    "not parse an invalid display" in new FromJsonScope {
      from("""{"version":1,"fields":[{"name":"foo","display":"textinput"}]}""") must throwA[IllegalArgumentException]
    }

    "provide an implicit Reads for parsing" in {
      import MetadataSchema.Json.reads
      val result = Json.parse("""{"version":1,"fields":[{"name":"foo","type":"String","display":"TextInput"}]}""").as[MetadataSchema]
      result must beEqualTo(MetadataSchema(1, Seq(MetadataField("foo", MetadataFieldType.String, MetadataFieldDisplay.TextInput))))
    }
  }

  "::inferFromMetadataJson" should {
    trait InferScope extends BaseScope {
      def from(jsonString: String) = MetadataSchema.inferFromMetadataJson(Json.parse(jsonString).as[JsObject])
    }

    "give version 1" in new InferScope {
      from("""{"foo":"bar"}""").version must beEqualTo(1)
    }

    "parse String fields" in new InferScope {
      val result = from("""{"foo":"bar","moo":"mar"}""")
      result.fields must containTheSameElementsAs(Seq(
        MetadataField("foo", MetadataFieldType.String, MetadataFieldDisplay.TextInput),
        MetadataField("moo", MetadataFieldType.String, MetadataFieldDisplay.TextInput)
      ))
    }

    "parse empty JSON" in new InferScope {
      from("{}") must beEqualTo(MetadataSchema.empty)
    }
  }

  "::empty" should {
    trait EmptyScope extends BaseScope {
      val subject = MetadataSchema.empty
    }

    "use version 1" in new EmptyScope {
      subject.version must beEqualTo(1)
    }

    "not have any fields" in new EmptyScope {
      subject.fields must beEqualTo(Seq())
    }
  }
}
