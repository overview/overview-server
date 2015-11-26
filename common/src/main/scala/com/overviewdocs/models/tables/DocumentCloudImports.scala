package com.overviewdocs.models.tables

import java.time.Instant

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.DocumentCloudImport

class DocumentCloudImportsImpl(tag: Tag) extends Table[DocumentCloudImport](tag, "csv_import") {
  def id = column[Int]("id", O.PrimaryKey)
  def documentSetId = column[Long]("document_set_id")
  def query = column[String]("query")
  def username = column[String]("username")
  def password = column[String]("password")
  def splitPages = column[Boolean]("split_pages")
  def lang = column[String]("lang")
  def nIdListsFetched = column[Int]("n_id_lists_fetched")
  def nIdListsTotal = column[Option[Int]]("n_id_lists_total")
  def nFetched = column[Int]("n_fetched")
  def nTotal = column[Option[Int]]("n_total")
  def cancelled = column[Boolean]("cancelled")
  def createdAt = column[Instant]("created_at")

  def * = (
    id, documentSetId, query, username, password, splitPages, lang,
    nIdListsFetched, nIdListsTotal, nFetched, nTotal, cancelled, createdAt
  ).<>((DocumentCloudImport.apply _).tupled, DocumentCloudImport.unapply)

  def createAttributes = (
    documentSetId, query, username, password, splitPages, lang,
    nIdListsFetched, nIdListsTotal, nFetched, nTotal, cancelled, createdAt
  ).<>((DocumentCloudImport.CreateAttributes.apply _).tupled, DocumentCloudImport.CreateAttributes.unapply)
}

object DocumentCloudImports extends TableQuery(new DocumentCloudImportsImpl(_))
