package com.overviewdocs.metadata

import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json

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
      override val fields = Seq(MetadataField("foo", MetadataFieldType.String))
      json must /("fields") /#(0) /("name" -> "foo")
      json must /("fields") /#(0) /("type" -> "String")
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
      from("""{"version":1,"fields":[{"name":"foo","type":"String"}]}""").fields must beEqualTo(Seq(
        MetadataField("foo", MetadataFieldType.String)
      ))
    }

    "parse fields in order" in new FromJsonScope {
      from("""{"version":1,"fields":[{"name":"foo","type":"String"},{"name":"bar","type":"String"}]}""").fields must beEqualTo(Seq(
        MetadataField("foo", MetadataFieldType.String),
        MetadataField("bar", MetadataFieldType.String)
      ))
    }

    "not parse an invalid type" in new FromJsonScope {
      from("""{"version":1,"fields":[{"name":"foo","type":"string"}]}""") must throwA[IllegalArgumentException]
    }

    "provide an implicit Reads for parsing" in {
      import MetadataSchema.Json.reads
      val result = Json.parse("""{"version":1,"fields":[{"name":"foo","type":"String"}]}""").as[MetadataSchema]
      result must beEqualTo(MetadataSchema(1, Seq(MetadataField("foo", MetadataFieldType.String))))
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
