package com.overviewdocs.metadata

import play.api.libs.json.{JsArray,JsNumber,JsObject,JsString,JsValue,Json}
import scala.collection.immutable
import scala.util.{Failure,Success,Try}

/** Schema that describes a Document's metadata.
  *
  * Document metadata is encoded as JSON. See Metadata for more details.
  *
  * The `version` must always be 1: anything else is an error.
  */
case class MetadataSchema(version: Int, fields: immutable.Seq[MetadataField]) {
  def toJson: JsValue = JsObject(Vector(
    "version" -> JsNumber(1),
    "fields" -> JsArray(fields.map { field =>
      Json.obj(
        "name" -> field.name,
        "type" -> (field.fieldType match {
          case MetadataFieldType.String => "String"
        }),
        "display" -> (field.display match {
          case MetadataFieldDisplay.TextInput => "TextInput"
          case MetadataFieldDisplay.Div => "Div"
          case MetadataFieldDisplay.Pre => "Pre"
        }),
      )
    })
  ))
}

object MetadataSchema {
  def fromJson(json: JsValue): MetadataSchema = MetadataSchema.Json.parse(json)

  /** Given a JSON String, returns a MetadataSchema that will absorb as much
    * data as possible from that JSON string.
    *
    * If the JSON is invalid or not an Object, returns an empty schema.
    *
    * This method should be a last resort: it works with Strings, but when we
    * get new types beyond standard JSON ones, it won't work.
    */
  def inferFromMetadataJson(jsObject: JsObject): MetadataSchema = {
    val fields = jsObject.keys.map(key => MetadataField(key, MetadataFieldType.String))
    MetadataSchema(1, fields.toVector)
  }

  def empty: MetadataSchema = MetadataSchema(1, Vector())

  object Json {
    import play.api.libs.json.{Reads,JsPath,JsResultException,JsonValidationError}
    import play.api.libs.json.Reads._
    import play.api.libs.functional.syntax._

    private val badTypeError = JsonValidationError("Invalid \"type\" value")
    private val badDisplayError = JsonValidationError("Invalid \"display\" value")

    private implicit val metadataFieldTypeReads: Reads[MetadataFieldType] = JsStringReads.collect(badTypeError) {
      case JsString("String") => MetadataFieldType.String
    }

    private implicit val metadataFieldDisplayReads: Reads[MetadataFieldDisplay] = JsStringReads.collect(badDisplayError) {
      case JsString("TextInput") => MetadataFieldDisplay.TextInput
      case JsString("Div") => MetadataFieldDisplay.Div
      case JsString("Pre") => MetadataFieldDisplay.Pre
    }

    private implicit val metadataFieldReads: Reads[MetadataField] = (
      (JsPath \ "name").read[String] and
      (JsPath \ "type").readWithDefault(JsString("String")).andThen(metadataFieldTypeReads) and
      (JsPath \ "display").readWithDefault(JsString("TextInput")).andThen(metadataFieldDisplayReads)
    )(MetadataField.apply _)

    implicit val reads: Reads[MetadataSchema] = (
      (JsPath \ "version").read[Int](min(1) keepAnd max(1)) and
      (JsPath \ "fields").read[Vector[MetadataField]]
    )(MetadataSchema.apply _)

    def parse(json: JsValue): MetadataSchema = try {
      json.as[MetadataSchema]
    } catch {
      case e: JsResultException => throw new IllegalArgumentException(e)
    }
  }
}
