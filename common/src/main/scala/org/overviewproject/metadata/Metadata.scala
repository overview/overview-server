package org.overviewproject.metadata

import play.api.libs.json.{JsObject,JsString,JsValue,Reads}

/** User-supplied data about a document that adheres to a MetadataSchema.
  *
  * Metadata is represented as JSON to the database and API. The methods here
  * help build and manipulate Metadata, with the help of a MetadataSchema.
  *
  * There may be multiple JSON representations of the same metadata; none are
  * canonical.
  */
case class Metadata(schema: MetadataSchema, val json: JsObject = JsObject(Seq())) {
  /** Returns a new Metadata with the field set to the new value.
    *
    * @throws IllegalArgumentException if fieldName is not in the schema or
    *         does not specify a String.
    */
  def setString(fieldName: String, fieldValue: String) = {
    checkField(fieldName, MetadataFieldType.String)
    Metadata(schema, json.+((fieldName, JsString(fieldValue))))
  }

  /** Returns the String value at the given field.
    *
    * If the underlying JSON does not include the field or if the underlying
    * JSON's value is of the wrong type, this method returns `""`.
    *
    * @throws IllegalArgumentException if fieldName is not in the schema.
    */
  def getString(fieldName: String) = {
    checkField(fieldName, MetadataFieldType.String)
    (json \ fieldName).asOpt[String](Reads.StringReads).getOrElse("")
  }

  /** Throws an error iff the given field of the given type is not in the schema. */
  private def checkField(fieldName: String, fieldType: MetadataFieldType): Unit = {
    if (!schema.fields.exists { f => f.name == fieldName && f.fieldType == fieldType }) {
      throw new IllegalArgumentException("The metadata schema does not include a `foo2` of type `String`")
    }
  }
}
