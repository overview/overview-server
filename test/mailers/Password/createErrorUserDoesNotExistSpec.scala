package mailers.Password

class createErrorUserDoesNotExistSpec extends mailers.MailerSpecification {
  trait OurContext extends MailerScope {
    override lazy val mailer = createErrorUserDoesNotExist("email@example.org")
  }

  "createErrorUserAlreadyExists()" should {
    "send to the user and only the user" in new OurContext {
      mailer.recipients must beEqualTo(Seq("email@example.org"))
    }
  }
}
