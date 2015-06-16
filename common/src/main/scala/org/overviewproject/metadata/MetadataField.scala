package org.overviewproject.metadata

sealed trait MetadataFieldType
object MetadataFieldType {
  case object String extends MetadataFieldType
}

case class MetadataField(name: String, fieldType: MetadataFieldType)
