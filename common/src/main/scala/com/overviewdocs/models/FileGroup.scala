package com.overviewdocs.models

case class FileGroup(
  id: Long,
  userEmail: String,
  apiToken: Option[String],
  completed: Boolean,
  deleted: Boolean
)

object FileGroup {
  case class CreateAttributes(
    userEmail: String,
    apiToken: Option[String]
  )
}
