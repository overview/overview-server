package mailers.Password

class createSpec extends mailers.MailSpecification {
  trait OurContext extends MailScope {
    val user = fakeUser.copy(email="email@example.org", resetPasswordToken=Some("0123456789abcdef"))

    override def mail = create(user, "https://reset.me")
  }

  "create()" should {
    "send to the user and only the user" in new OurContext {
      mail.recipients.must(equalTo(Seq(user.email)))
    }

    "include the reset-password URL" in new OurContext {
      mail.text.must(contain("https://reset.me"))
    }
  }
}
