package mailers.User

import org.specs2.mock.Mockito

import models.{ConfirmationRequest, OverviewUser}

class createSpec extends mailers.MailerSpecification {
  trait OurContext extends MailerScope with Mockito {
    trait UserType extends models.OverviewUser with models.ConfirmationRequest
    val user = mock[UserType]
    user.email returns "email@example.org"
    user.confirmationToken returns "0123456789abcdef"

    override lazy val mailer = create(user)
  }

  "create()" should {
    "send to the user and only the user" in new OurContext {
      mailer.recipients.must(equalTo(Seq(user.email)))
    }

    "include the confirmation URL" in new OurContext {
      mailer.text.must(contain(user.confirmationToken)) 
    }
  }
}
