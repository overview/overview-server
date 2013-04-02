package models.orm.stores

import org.squeryl.Query
import org.overviewproject.postgres.SquerylEntrypoint._

import models.orm.DocumentSetUser
import models.orm.Schema

object DocumentSetUserStore {
  def insertOrUpdate(documentSetUser: DocumentSetUser): DocumentSetUser = {
    Schema.documentSetUsers.insertOrUpdate(documentSetUser)
  }

  def delete(documentSetUserQuery: Query[DocumentSetUser]): Unit = {
    Schema.documentSetUsers.delete(documentSetUserQuery)
  }

  def delete(documentSetUser: DocumentSetUser): Unit = {
    Schema.documentSetUsers.delete(documentSetUser.id) // ignore 0-rows-deleted
  }
}
