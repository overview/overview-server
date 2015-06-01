package models.tables

import java.util.UUID
import java.sql.Timestamp

import models.Session
import org.overviewproject.database.Slick.simple._
import org.overviewproject.postgres.InetAddress

class SessionsImpl(tag: Tag) extends Table[Session](tag, "session") {
  def id = column[UUID]("id", O.PrimaryKey)
  def userId = column[Long]("user_id")
  def ip = column[InetAddress]("ip")(ipColumnType)
  def createdAt = column[Timestamp]("created_at")
  def updatedAt = column[Timestamp]("updated_at")

  def * = (id, userId, ip, createdAt, updatedAt) <> (
    (Session.apply(_: UUID, _: Long, _: InetAddress, _: Timestamp, _: Timestamp)).tupled,
    Session.unapply
  )
}

object Sessions extends TableQuery(new SessionsImpl(_))
