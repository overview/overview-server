package mailers.Password

import controllers.util.NullMessagesApi

class createErrorUserDoesNotExistSpec extends mailers.MailerSpecification {
  trait OurContext extends MailerScope {
    override lazy val mailer = createErrorUserDoesNotExist(
      "email@example.org",
      "http://example.org"
    )(NullMessagesApi.messages)
  }

  "createErrorUserAlreadyExists()" should {
    "send to the user and only the user" in new OurContext {
      mailer.recipients must beEqualTo(Seq("email@example.org"))
    }
  }
}
