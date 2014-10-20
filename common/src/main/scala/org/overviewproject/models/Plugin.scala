package org.overviewproject.models

import java.util.UUID

/** A plugin: a website that helps us interact with documents.
  *
  * Plugins aren't actually stored in Overview: they're stored elsewhere on the
  * Internet. But Overview maintains a _registry_ that points to the actual
  * plugins. Plugin objects within Overview are retistry entries.
  */
case class Plugin(
  id: UUID,
  name: String,
  description: String,
  url: String
)

object Plugin {
  case class CreateAttributes(
    name: String,
    description: String,
    url: String
  )

  case class UpdateAttributes(
    name: String,
    description: String,
    url: String
  )

  def build(attributes: CreateAttributes) = apply(
    id=UUID.randomUUID(),
    name=attributes.name,
    description=attributes.description,
    url=attributes.url
  )
}
