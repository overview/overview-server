package models.orm

import java.sql.Timestamp
import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.annotations.{Column,Transient}
import scala.annotation.target.field

case class LogEntry(
    val id: Long = 0L,
    @Column("document_set_id")
    val documentSetId: Long,
    @Column("user_id")
    val userId: Long,
    val date: Timestamp,
    val component: String,
    val action: String = "",
    val details: String = "",
    @(Transient @field)
    val providedUser: Option[User] = None
    ) extends KeyedEntity[Long] {

  lazy val user : User = providedUser.getOrElse(Schema.userLogEntries.right(this).single)
  lazy val documentSet = Schema.documentSetLogEntries.right(this)

  def save = Schema.logEntries.insertOrUpdate(this)
}

object LogEntry {
  def query = from(Schema.logEntries)(l => select(l))

  object ImplicitHelper {
    class LogEntrySeq(logEntries: Seq[LogEntry]) {
      private lazy val usersMap : Map[Long,User] = {
        from(Schema.users)(u =>
          where(u.id in logEntries.map(_.userId).distinct)
          .select(u)
        ).map(u => u.id -> u).toMap
      }

      def withUsers = {
        logEntries.map(le => le.copy(providedUser=usersMap.get(le.userId)))
      }
    }

    implicit def seqLogEntryToLogEntrySeq(logEntries: Seq[LogEntry]) = new LogEntrySeq(logEntries)
  }
}
