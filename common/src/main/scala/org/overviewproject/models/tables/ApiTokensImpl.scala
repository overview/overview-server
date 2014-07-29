package org.overviewproject.models.tables

import java.sql.Timestamp

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.ApiToken

class ApiTokensImpl(tag: Tag) extends Table[ApiToken](tag, "api_token") {
  def token = column[String]("token", O.PrimaryKey)
  def createdAt = column[Timestamp]("created_at")
  def createdBy = column[String]("created_by")
  def description = column[String]("description")
  def documentSetId = column[Long]("document_set_id")

  def * = (token, createdAt, createdBy, description, documentSetId) <> ((ApiToken.apply _).tupled, ApiToken.unapply)
}

object apiTokens extends TableQuery(new ApiTokensImpl(_))
