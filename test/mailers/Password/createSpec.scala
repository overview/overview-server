package mailers.Password

import controllers.util.NullMessagesApi
import models.User

class createSpec extends mailers.MailerSpecification {
  trait OurContext extends MailerScope {
    val user = User(email="email@example.org", resetPasswordToken=Some("0123456789abcdef"))

    override lazy val mailer = create(user, "https://reset.me")(NullMessagesApi.messages)
  }

  "create()" should {
    "send to the user and only the user" in new OurContext {
      mailer.recipients.must(equalTo(Seq(user.email)))
    }

    "include the reset-password URL" in new OurContext {
      mailer.text.must(contain("https://reset.me"))
    }
  }
}
