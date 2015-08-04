package com.overviewdocs.metadata

import play.api.libs.json.{JsArray,JsNumber,JsObject,JsString,JsValue,Json}

/** Schema that describes a Document's metadata.
  *
  * Document metadata is encoded as JSON. See Metadata for more details.
  *
  * The `version` must always be 1: anything else is an error.
  */
case class MetadataSchema(version: Int, fields: Seq[MetadataField]) {
  def toJson: JsValue = JsObject(Seq(
    "version" -> JsNumber(1),
    "fields" -> JsArray(fields.map { field =>
      Json.obj(
        "name" -> field.name,
        "type" -> (field.fieldType match {
          case MetadataFieldType.String => "String"
        })
      )
    })
  ))
}

object MetadataSchema {
  def fromJson(json: JsValue): MetadataSchema = MetadataSchema.Json.parse(json)

  def empty: MetadataSchema = MetadataSchema(1, Seq())

  object Json {
    import play.api.data.validation.ValidationError
    import play.api.libs.json.{JsPath,JsResultException}
    import play.api.libs.json.Reads
    import play.api.libs.json.Reads._
    import play.api.libs.functional.syntax._

    private val badTypeError = ValidationError("Invalid \"type\" value")

    private implicit val metadataFieldTypeReads: Reads[MetadataFieldType] = StringReads.collect(badTypeError) {
      case "String" => MetadataFieldType.String
    }

    private implicit val metadataFieldReads: Reads[MetadataField] = (
      (JsPath \ "name").read[String] and
      (JsPath \ "type").read[MetadataFieldType]
    )(MetadataField.apply _)

    implicit val reads: Reads[MetadataSchema] = (
      (JsPath \ "version").read[Int](min(1) keepAnd max(1)) and
      (JsPath \ "fields").read[Seq[MetadataField]]
    )(MetadataSchema.apply _)

    def parse(json: JsValue): MetadataSchema = try {
      json.as[MetadataSchema]
    } catch {
      case e: JsResultException => throw new IllegalArgumentException(e)
    }
  }
}
