package mailers.User

import play.api.i18n.Messages
import play.api.mvc.RequestHeader

import mailers.Mail
import models.User

case class createErrorUserAlreadyExists(val user: User, loginUrl: String)(implicit val messages: Messages) extends Mail {
  private val m = views.Magic.scopedMessages("mailers.User.createErrorUserAlreadyExists")

  private val intro = m("intro")
  private val case1 = m("case1")
  private val case2 = m("case2")
  private val signoff = m("signoff")
  private val signature = m("signature")

  override val recipients = Seq(user.email)
  override val subject = m("subject")
  override val text = intro + "\n\n" + case1 + "\n\n" + case2 + "\n\n" + loginUrl + "\n\n" + signoff + "\n" + signature
  override val html =
    <html lang={messages.lang.code}>
      <head>
        <title>{subject}</title>
      </head>
      <body>
        <p>{intro}</p>
        <p>{case1}</p>
        <p>{case2}</p>
        <p><a href={loginUrl}>{loginUrl}</a></p>
        <p>{signoff}<br/>{signature}</p>
      </body>
    </html>
}
