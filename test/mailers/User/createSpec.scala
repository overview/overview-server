package mailers.User

import controllers.util.NullMessagesApi
import models.User

class createSpec extends mailers.MailerSpecification {
  trait OurContext extends MailerScope {
    override lazy val mailer = create(
      User(email="user@example.org", confirmationToken=Some("0123456789abcdef")),
      "https://confirm.me",
      "https://contact.us"
    )(NullMessagesApi.messages)
  }

  "create()" should {
    "send to the user and only the user" in new OurContext {
      mailer.recipients must beEqualTo(Seq("user@example.org"))
    }

    "include the confirmation URL" in new OurContext {
      mailer.text must contain("https://confirm.me")
    }
  }
}
