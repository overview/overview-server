package org.overviewproject.models

case class Tag(
  id: Long,
  documentSetId: Long,
  name: String,
  color: String
) {
  def update(attributes: Tag.UpdateAttributes) = copy(
    name=attributes.name,
    color=attributes.color
  )
}

object Tag {
  case class CreateAttributes(name: String, color: String)
  case class UpdateAttributes(name: String, color: String)
}
