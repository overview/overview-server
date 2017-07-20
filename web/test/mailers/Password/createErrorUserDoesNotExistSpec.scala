package mailers.Password

import controllers.util.NullMessagesApi

class createErrorUserDoesNotExistSpec extends mailers.MailSpecification {
  trait OurContext extends MailScope {
    override def mail = createErrorUserDoesNotExist(
      "email@example.org",
      "http://example.org"
    )(NullMessagesApi.messages)
  }

  "createErrorUserAlreadyExists()" should {
    "send to the user and only the user" in new OurContext {
      mail.recipients must beEqualTo(Seq("email@example.org"))
    }
  }
}
