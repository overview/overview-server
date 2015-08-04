package com.overviewdocs.metadata

sealed trait MetadataFieldType
object MetadataFieldType {
  /** String value.
    *
    * The default value is "".
    */
  case object String extends MetadataFieldType
}

case class MetadataField(name: String, fieldType: MetadataFieldType)
