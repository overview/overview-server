package com.overviewdocs.metadata

import play.api.libs.json.{JsArray,JsBoolean,JsDefined,JsNull,JsObject,JsNumber,JsString,JsValue,JsUndefined}

/** User-supplied data about a document that adheres to a MetadataSchema.
  *
  * Metadata is represented as JSON to the database and API. The methods here
  * help build and manipulate Metadata, with the help of a MetadataSchema.
  *
  * There may be multiple JSON representations of the same metadata; none are
  * canonical.
  */
case class Metadata(schema: MetadataSchema, val json: JsObject = JsObject(Seq())) {
  /** Returns the metadata as JSON.
    *
    * In the course of normal operation, it is easy for a user to make a
    * Document's `metadataJson` non-compliant with the schema. That's okay:
    * we sanitize it on output.
    *
    * Use this method whenever you are returning JSON to the user.
    */
  def cleanJson: JsObject = {
    val parts: Seq[(String,JsValue)] = schema.fields
      .map((field) => (field.name -> JsString(getString(field.name))))
    JsObject(parts)
  }

  /** Returns a new Metadata with the field set to the new value.
    *
    * @throws IllegalArgumentException if fieldName is not in the schema or
    *         does not specify a String.
    */
  def setString(fieldName: String, fieldValue: String): Metadata = {
    checkFieldAndType(fieldName, MetadataFieldType.String)
    Metadata(schema, json.+((fieldName, JsString(fieldValue))))
  }

  /** Returns the String value at the given field.
    *
    * If the underlying JSON does not include the field or if the underlying
    * JSON's value is of the wrong type, this method returns `""`.
    *
    * @throws IllegalArgumentException if fieldName is not in the schema.
    */
  def getString(fieldName: String): String = {
    checkField(fieldName)
    (json \ fieldName) match {
      case JsDefined(JsString(s)) => s
      case JsDefined(JsNull) => ""
      case JsDefined(v) => v.toString
      case _ => ""
    }
  }

  /** Throws an err iff the given field is not in the schema. */
  private def checkField(fieldName: String): Unit = {
    if (!schema.fields.exists { _.name == fieldName }) {
      throw new IllegalArgumentException(s"The metadata schema does not include a `$fieldName`")
    }
  }

  /** Throws an error iff the given field of the given type is not in the schema. */
  private def checkFieldAndType(fieldName: String, fieldType: MetadataFieldType): Unit = {
    if (!schema.fields.exists { f => f.name == fieldName && f.fieldType == fieldType }) {
      throw new IllegalArgumentException(s"The metadata schema does not include a `$fieldName` of type `$fieldType`")
    }
  }
}
