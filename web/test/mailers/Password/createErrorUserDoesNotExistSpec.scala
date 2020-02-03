package mailers.Password

import test.helpers.MockMessages

class createErrorUserDoesNotExistSpec extends mailers.MailSpecification {
  trait OurContext extends MailScope {
    override def mail = createErrorUserDoesNotExist(
      "email@example.org",
      "http://example.org"
    )(MockMessages.default)
  }

  "createErrorUserAlreadyExists()" should {
    "send to the user and only the user" in new OurContext {
      mail.recipients must beEqualTo(Seq("email@example.org"))
    }
  }
}
