package mailers.User

import org.specs2.mock.Mockito

class createErrorUserAlreadyExistsSpec extends mailers.MailerSpecification {
  trait OurContext extends MailerScope with Mockito {
    val user = smartMock[models.OverviewUser]
    user.email returns "email@example.org"

    override lazy val mailer = createErrorUserAlreadyExists(user)
  }

  "createErrorUserAlreadyExists()" should {
    "send to the user and only the user" in new OurContext {
      mailer.recipients.must(equalTo(Seq(user.email)))
    }
  }
}
