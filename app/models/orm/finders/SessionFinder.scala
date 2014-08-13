package models.orm.finders

import scala.language.postfixOps
import scala.language.implicitConversions

import java.util.UUID
import org.squeryl.Query

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{Finder, FinderResult}
import models.orm.Schema
import models.{Session, User}

trait SessionFinder extends Finder {
  class SessionFinderResult(query: Query[Session]) extends FinderResult(query) {
    def withUsers : FinderResult[(Session,User)] = {
      join(toQuery, Schema.users)((s, u) =>
        select(s, u)
        on(s.userId === u.id)
      )
    }

    def expired = new SessionFinderResult(query.where(_.createdAt < oldestAllowedCreatedAt))
    def notExpired = new SessionFinderResult(query.where(_.createdAt >= oldestAllowedCreatedAt))
  }

  implicit private def queryToSessionFinderResult(query: Query[Session]) : SessionFinderResult = new SessionFinderResult(query)

  private val maxAgeInMs = 30L * 86400 * 1000 // 30 days
  def oldestAllowedCreatedAt = new java.sql.Timestamp(new java.util.Date().getTime() - maxAgeInMs)

  /** @return All Sessions with the given ID (max 1 row). */
  def byId(id: UUID) : SessionFinderResult = Schema.sessions.where(_.id === id)

  /** @return All Sessions with the given user ID */
  def byUserId(userId: Long) : SessionFinderResult = Schema.sessions.where(_.userId === userId)
}

object SessionFinder extends SessionFinder
