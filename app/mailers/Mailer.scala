package mailers

import javax.inject.Inject
import play.api.Configuration
import play.api.libs.mailer.{Email,MailerClient,MockMailer}

class Mailer @Inject() (configuration: Configuration, mailerClient: MailerClient) {
  private lazy val fromEmail: String = configuration.getString("mail.from").get

  /** We use our own mocking config: it's easier for users.
    *
    * If host is empty, mock. Otherwise, don't mock.
    *
    * Rather than rewrite play-mailer, we coded this runtime check.
    */
  private lazy val isMock: Boolean = configuration.getString("play.mailer.host").getOrElse("").isEmpty

  def send(mail: Mail) = {
    val email = mail.toEmail(fromEmail)

    if (isMock) {
      new MockMailer().send(email)
    } else {
      mailerClient.send(email)
    }
  }
}
