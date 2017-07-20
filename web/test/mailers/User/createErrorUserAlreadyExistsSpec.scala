package mailers.User

class createErrorUserAlreadyExistsSpec extends mailers.MailSpecification {
  trait OurContext extends MailScope {
    override def mail = createErrorUserAlreadyExists(
      fakeUser.copy(email="user@example.org"),
      "http://example.org"
    )
  }

  "createErrorUserAlreadyExists()" should {
    "send to the user and only the user" in new OurContext {
      mail.recipients.must(equalTo(Seq("user@example.org")))
    }
  }
}
