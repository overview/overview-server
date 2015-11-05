package mailers.User

import models.User

class createErrorUserAlreadyExistsSpec extends mailers.MailerSpecification {
  trait OurContext extends MailerScope {
    override lazy val mailer = createErrorUserAlreadyExists(User(email="user@example.org"))
  }

  "createErrorUserAlreadyExists()" should {
    "send to the user and only the user" in new OurContext {
      mailer.recipients.must(equalTo(Seq("user@example.org")))
    }
  }
}
