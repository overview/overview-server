package mailers.User

import models.{ConfirmationRequest, OverviewUser}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Play.{start,stop}
import play.api.i18n.Lang
import play.api.test.{FakeApplication,FakeRequest}

class createSpec extends Specification {
  step(start(FakeApplication()))

  trait OurContext extends Scope {
    val user = new OverviewUser with ConfirmationRequest {
      val id = 5l
      val email = "email@example.org"
      override def currentSignInAt = None
      override def currentSignInIp = None
      override def lastSignInAt = None
      override def lastSignInIp = None
      override def recordLogin(ip: String, date: java.util.Date) = this
      def passwordMatches(password: String) = true
      def withConfirmationRequest = Some(this)
      def save {}
      
      val confirmationToken = "token"
      val confirmationSentAt = new java.sql.Timestamp(0)
      def confirm = this
    }
    
    val lang = Lang("fu", "BA")
    val request = FakeRequest("POST", "https://example.org/user/create")

    lazy val mailer = create(user)(lang, request)
  }

  "create()" should {
    "send to the user and only the user" in new OurContext {
      mailer.recipients.must(equalTo(Seq(user.email)))
    }

    "include the confirmation URL" in new OurContext {
      mailer.text.must(contain(user.confirmationToken)) 
    }
  }

  step(stop)
}
