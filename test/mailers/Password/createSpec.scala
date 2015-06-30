package mailers.Password

import org.specs2.mock.Mockito

import models.{OverviewUser,ResetPasswordRequest}

class createSpec extends mailers.MailerSpecification {
  trait OurContext extends MailerScope with Mockito {
    trait UserType extends models.OverviewUser with models.ResetPasswordRequest
    val user = smartMock[UserType]
    user.email returns "email@example.org"
    user.resetPasswordToken returns "0123456789abcdef"

    override lazy val mailer = create(user)
  }

  "create()" should {
    "send to the user and only the user" in new OurContext {
      mailer.recipients.must(equalTo(Seq(user.email)))
    }

    "include the reset-password URL" in new OurContext {
      mailer.text.must(contain(user.resetPasswordToken)) 
    }
  }
}
