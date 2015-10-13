package com.overviewdocs.models

case class Page(
  id: Long,
  fileId: Long,
  pageNumber: Int,
  dataLocation: String,
  dataSize: Long,
  text: String,
  isFromOcr: Boolean
)

object Page {
  case class CreateAttributes(
    fileId: Long,
    pageNumber: Int,
    dataLocation: String,
    dataSize: Long,
    text: String,
    isFromOcr: Boolean
  )
  
  case class ReferenceAttributes(
    id: Long,
    fileId: Long,
    pageNumber: Int,
    text: String,
    isFromOcr: Boolean
  )
}
