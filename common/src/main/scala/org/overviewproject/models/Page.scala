package com.overviewdocs.models

case class Page(
  id: Long,
  fileId: Long,
  pageNumber: Int,
  dataLocation: String,
  dataSize: Long,
  data: Option[Array[Byte]],
  text: Option[String],
  dataErrorMessage: Option[String] = None,
  textErrorMessage: Option[String] = None
)

object Page {
  case class CreateAttributes(
    fileId: Long,
    pageNumber: Int,
    dataLocation: String,
    dataSize: Long,
    text: String
  )
  
  case class ReferenceAttributes(
    id: Long,
    fileId: Long,
    pageNumber: Int,
    text: String
  )
}
