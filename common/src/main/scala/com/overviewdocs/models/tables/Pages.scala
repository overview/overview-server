package com.overviewdocs.models.tables

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.Page

class PagesImpl(tag: Tag) extends Table[Page](tag, "page") {
  def id = column[Long]("id", O.PrimaryKey)
  def fileId = column[Long]("file_id")
  def pageNumber = column[Int]("page_number")
  def dataLocation = column[String]("data_location")
  def dataSize = column[Long]("data_size")
  def text = column[String]("text")
  def isFromOcr = column[Boolean]("is_from_ocr")

  def * = (id, fileId, pageNumber, dataLocation, dataSize, text, isFromOcr) <> ((Page.apply _).tupled, Page.unapply)

  def createAttributes = (fileId, pageNumber, dataLocation, dataSize, text, isFromOcr)
    .<>(Page.CreateAttributes.tupled, Page.CreateAttributes.unapply)

  def referenceAttributes = (id, fileId, pageNumber, text, isFromOcr)
    .<>(Page.ReferenceAttributes.tupled, Page.ReferenceAttributes.unapply)
}

object Pages extends TableQuery(new PagesImpl(_))
