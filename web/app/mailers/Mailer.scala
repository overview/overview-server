package mailers

import javax.inject.Inject
import play.api.Configuration
import play.api.libs.mailer.{Email,MailerClient,MockMailer}

class Mailer @Inject() (configuration: Configuration, mailerClient: MailerClient) {
  private lazy val fromEmail: String = configuration.get[String]("mail.from")

  /** We use our own mocking config: it's easier for users.
    *
    * If host is empty, mock. Otherwise, don't mock.
    *
    * Rather than rewrite play-mailer, we coded this runtime check.
    */
  private lazy val isMock: Boolean = configuration.get[String]("play.mailer.host").isEmpty

  def send(mail: Mail) = {
    val email = mail.toEmail(fromEmail)

    if (isMock) {
      new MockMailer().send(email)
    } else {
      mailerClient.send(email)
    }
  }
}
