package com.overviewdocs.models

import com.overviewdocs.tree.orm.{Tag=>DeprecatedTag}

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

  def toDeprecatedTag: DeprecatedTag = DeprecatedTag(documentSetId, name, color, id)
}

object Tag {
  case class CreateAttributes(name: String, color: String)
  case class UpdateAttributes(name: String, color: String)
}
