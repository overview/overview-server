package com.overviewdocs.metadata

/** How a field is stored in JSON. */
sealed trait MetadataFieldType
object MetadataFieldType {
  /** String value.
    *
    * The default value is "".
    */
  case object String extends MetadataFieldType
}

/** How a field is presented to the user.
  *
  * Enumeration values are named as though the fields will be displayed in an
  * HTML5 form.
  *
  * If you're writing presentation logic, beware the user. Your display should
  * work with very-long values. A single-line text input should scroll; a
  * multi-line text block should break long words; et cetera.
  */
sealed trait MetadataFieldDisplay
object MetadataFieldDisplay {
  /** Display in a text input. */
  case object TextInput extends MetadataFieldDisplay

  /** Display as text, without an editing interface.
    *
    * This is just a suggestion. There is no way to make a field read-only.
    */
  case object Div extends MetadataFieldDisplay

  /** Display as monospace text, without an editing interface.
    *
    * This is just a suggestion. There is no way to make a field read-only.
    */
  case object Pre extends MetadataFieldDisplay
}

case class MetadataField(
  name: String,
  fieldType: MetadataFieldType = MetadataFieldType.String,
  display: MetadataFieldDisplay = MetadataFieldDisplay.TextInput
)
