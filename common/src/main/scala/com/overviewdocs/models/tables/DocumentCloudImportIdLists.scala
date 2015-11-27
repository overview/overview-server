package com.overviewdocs.models.tables

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.DocumentCloudImportIdList

class DocumentCloudImportIdListsImpl(tag: Tag)
extends Table[DocumentCloudImportIdList](tag, "document_cloud_import_id_list") {
  def id = column[Int]("id", O.PrimaryKey)
  def documentCloudImportId = column[Int]("document_cloud_import_id")
  def pageNumber = column[Int]("page_number")
  def idsString = column[String]("ids_string")
  def nDocuments = column[Int]("n_documents")
  def nPages = column[Int]("n_pages")

  def * = (id, documentCloudImportId, pageNumber, idsString, nDocuments, nPages)
    .<>((DocumentCloudImportIdList.apply _).tupled, DocumentCloudImportIdList.unapply)

  def createAttributes = (documentCloudImportId, pageNumber, idsString, nDocuments, nPages).<>(
    (DocumentCloudImportIdList.CreateAttributes.apply _).tupled,
    DocumentCloudImportIdList.CreateAttributes.unapply
  )
}

object DocumentCloudImportIdLists extends TableQuery(new DocumentCloudImportIdListsImpl(_))
