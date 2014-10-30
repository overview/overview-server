package models

case class DocumentFileInfo(
  title: Option[String],
  fileId: Option[Long],
  pageId: Option[Long],
  pageNumber: Option[Int]
)