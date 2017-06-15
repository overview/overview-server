package mailers.User

class createSpec extends mailers.MailSpecification {
  trait OurContext extends MailScope {
    override def mail = create(
      fakeUser.copy(email="user@example.org", confirmationToken=Some("0123456789abcdef")),
      "https://confirm.me",
      "https://contact.us"
    )
  }

  "create()" should {
    "send to the user and only the user" in new OurContext {
      mail.recipients must beEqualTo(Seq("user@example.org"))
    }

    "include the confirmation URL" in new OurContext {
      mail.text must contain("https://confirm.me")
    }
  }
}
