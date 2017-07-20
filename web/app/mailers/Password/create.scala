package mailers.Password

import play.api.i18n.Messages

import mailers.Mail
import models.User

case class create(val user: User, val resetPasswordUrl: String)(implicit val messages: Messages) extends Mail {
  private val m = views.Magic.scopedMessages("mailers.Password.create")

  private val body = m("body")
  private val signoff = m("signoff")
  private val case1 = m("case1")
  private val case2 = m("case2")
  private val signature = m("signature")

  override val recipients = Seq(user.email)
  override val subject = m("subject")
  override val text = Seq(body, case1, case2, resetPasswordUrl, signoff + "\n" + signature).mkString("\n\n")
  override val html =
    <html lang={messages.lang.code}>
      <head>
        <title>{subject}</title>
      </head>
      <body>
        <p>{body}</p>
        <p>{case1}</p>
        <p>{case2}</p>
        <p><a href={resetPasswordUrl}>{resetPasswordUrl}</a></p>
        <p>{signoff}<br/>{signature}</p>
      </body>
    </html>
}
