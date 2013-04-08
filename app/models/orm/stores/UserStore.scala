package models.orm.stores

import org.squeryl.Query

import org.overviewproject.postgres.SquerylEntrypoint._
import models.orm.{ Schema, User }

object UserStore {
  def insertOrUpdate(user: User): User = {
    Schema.users.insertOrUpdate(user)
  }

  def delete(userQuery: Query[User]): Unit = {
    Schema.users.delete(userQuery)
  }

  def delete(user: User): Unit = {
    Schema.users.delete(user.id)
  }
}
