package mailers.User

import controllers.util.NullMessagesApi
import models.User

class createErrorUserAlreadyExistsSpec extends mailers.MailerSpecification {
  trait OurContext extends MailerScope {
    override lazy val mailer = createErrorUserAlreadyExists(
      User(email="user@example.org"),
      "http://example.org"
    )(NullMessagesApi.messages)
  }

  "createErrorUserAlreadyExists()" should {
    "send to the user and only the user" in new OurContext {
      mailer.recipients.must(equalTo(Seq("user@example.org")))
    }
  }
}
