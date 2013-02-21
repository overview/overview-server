package models.orm

import java.sql.Timestamp
import org.squeryl.annotations.{ Column, Transient }
import org.squeryl.dsl.ManyToMany
import org.squeryl.{KeyedEntity,Query}
import org.overviewproject.postgres.SquerylEntrypoint._
import scala.annotation.target.field
import ua.t3hnar.bcrypt._

import models.orm.DocumentSetType.DocumentCloudDocumentSet

case class User(
  val id: Long = 0L,
  val email: String = "user@example.org",
  @Column("password_hash") var passwordHash: String = "",
  var role: UserRole.UserRole = UserRole.NormalUser,
  @Column("confirmation_token") var confirmationToken: Option[String] = None,
  @Column("confirmation_sent_at") var confirmationSentAt: Option[Timestamp] = None,
  @Column("confirmed_at") var confirmedAt: Option[Timestamp] = None,
  @Column("reset_password_token") var resetPasswordToken: Option[String] = None,
  @Column("reset_password_sent_at") var resetPasswordSentAt: Option[Timestamp] = None,
  @Column("current_sign_in_at") var currentSignInAt: Option[Timestamp] = None,
  @Column("current_sign_in_ip") var currentSignInIp: Option[String] = None,
  @Column("last_sign_in_at") var lastSignInAt: Option[Timestamp] = None,
  @Column("last_sign_in_ip") var lastSignInIp: Option[String] = None,
  @Column("last_activity_at") var lastActivityAt: Option[Timestamp] = None,
  @Column("last_activity_ip") var lastActivityIp: Option[String] = None
  ) extends KeyedEntity[Long] {

  def this() = this(role = UserRole.NormalUser)
  
  lazy val orderedDocumentSets: Query[DocumentSet] =
    from(Schema.documentSets, Schema.documentSetUsers)((ds, dsu) => 
      where(dsu.userEmail === email and dsu.documentSetId === ds.id) 
      select(ds)
      orderBy(ds.createdAt.desc))

  def createDocumentSet(query: String): DocumentSet = {
    require(id != 0l)

    val documentSet = Schema.documentSets.insert(new DocumentSet(DocumentCloudDocumentSet, 0L, query=Some(query)))
    Schema.documentSetUsers.insert(DocumentSetUser(documentSet.id, email))

    documentSet
  }

  // https://www.assembla.com/spaces/squeryl/tickets/68-add-support-for-full-updates-on-immutable-case-classes#/followers/ticket:68
  override def isPersisted(): Boolean = (id > 0)

  def save: User = Schema.users.insertOrUpdate(this)

  def delete = Schema.users.delete(id)
}

object User {
  private val TokenLength = 26
  private val BcryptRounds = 7

  //def all() = from(Schema.users)(u => select(u).orderBy(u.email.asc))
  def all() = from(Schema.users)(u => select(u).orderBy(u.lastActivityAt.isNull, u.lastActivityAt.desc, u.confirmedAt.desc))

  def findById(id: Long) = Schema.users.lookup(id)

  def findByEmail(email: String): Option[User] = {
    Schema.users.where(u => lower(u.email) === lower(email)).headOption
  }

  def findByConfirmationToken(token: String): Option[User] = {
    Schema.users.where(u => u.confirmationToken === Some(token)).headOption
  }

  def findByResetPasswordTokenAndMinDate(token: String, minDate: java.util.Date): Option[User] = {
    Schema.users
      .where(u => u.resetPasswordToken === Some(token))
      .where(u => u.resetPasswordSentAt >= Some(new Timestamp(minDate.getTime)))
      .headOption
  }
}
