package models.orm


import java.sql.Timestamp
import models.orm.Dsl.{crypt,gen_hash}
import org.joda.time.DateTime.now
import org.squeryl.annotations.{Column, Transient}
import org.squeryl.dsl.{ManyToMany, OneToMany}
import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode._
import scala.annotation.target.field
import ua.t3hnar.bcrypt._



case class User(
    val id: Long = 0l,
    val email: String,
    //var role: UserRole.UserRole,
    @Column("password_hash")
    var passwordHash: String,
    @Column("confirmation_token")
    var confirmationToken: Option[String] = None,
    @Column("confirmation_sent_at")
    var confirmationSentAt: Option[Timestamp] = None,
    @Column("confirmed_at")
    var confirmedAt: Option[Timestamp] = None
    //@Column("reset_password_token")
    //var resetPasswordToken: Option[String],
    //@Column("reset_password_sent_at")
    //var resetPasswordSentAt: Option[DateTime],
    //@Column("current_sign_in_at")
    //var currentSignInAt: Option[DateTime],
    //@Column("current_sign_in_ip")
    //var currentSignInIp: Option[String],
    //@Column("last_sign_in_at")
    //var lastSignInAt: Option[DateTime],
    //@Column("last_sign_in_ip")
    //var lastSignInIp: Option[String]
    ) extends KeyedEntity[Long] {

  private var validCredentials = false
  
  lazy val documentSets: ManyToMany[DocumentSet, DocumentSetUser] = 
    Schema.documentSetUsers.right(this)
  
  def createDocumentSet(query: String): DocumentSet = {
    require(id != 0l)

    val documentSet = Schema.documentSets.insert(new DocumentSet(0L, query))
    documentSets.associate(documentSet)
    
    documentSet
  }

  def withConfirmation: User = 
    copy(confirmedAt = Some(new Timestamp(now().getMillis)),
         confirmationToken = None)
      
  def hasValidCredentials: Boolean = {
    validCredentials
  }
  
  def isConfirmed : Boolean = {
    confirmedAt.isDefined
  }
  
  def save = {
    Schema.users.insertOrUpdate(this)
  }
}

object User {
  private val TokenLength = 26
  private val BcryptRounds = 7
  
  def apply(email: String, rawPassword: String) : User = {
    val storedUser =  Schema.users.where(u => u.email === email).headOption
    
    storedUser match {
      case None => User(email = email, passwordHash = rawPassword.bcrypt(BcryptRounds))
      case Some(u) => {
        u.validCredentials = rawPassword.isBcrypted(u.passwordHash)
        u
      }
    }
  } 
    
  def findById(id: Long) = Schema.users.lookup(id)

  def findByEmail(email: String) : Option[User] = {
    from(Schema.users)(u => where(u.email === email) select(u)).headOption
  }
  
  def findByConfirmationToken(token: String): Option[User] = {
    Schema.users.where(u => u.confirmationToken.getOrElse("") === token).headOption
  }
  
  def prepareNewRegistration(email: String, password: String) : User = {
    val confirmationToken = util.Random.alphanumeric take(TokenLength) mkString;
    val confirmationSentAt = new Timestamp(now().getMillis())
    
    User(email = email, passwordHash = password.bcrypt(BcryptRounds), 
        confirmationToken = Some(confirmationToken), 
        confirmationSentAt = Some(confirmationSentAt))
  }
  
  def isConfirmed(email: String): Boolean = false
}
