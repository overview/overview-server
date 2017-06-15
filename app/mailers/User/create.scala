package mailers.User

import play.api.i18n.Messages
import mailers.Mailer
import models.User
import scala.xml.Unparsed

case class create(val user: User, val url: String, val contactUrl: String)(implicit val messages: Messages) extends Mailer {
  private val m = views.Magic.scopedMessages("mailers.User.create")

  private val body1 = m("body1")
  private val body2 = Unparsed(m("body2_html", contactUrl))
  private val signoff = m("signoff")
  private val signature = m("signature")

  override val recipients = Seq(user.email)
  override val subject = m("subject")
  override val text = body1 + "\n\n" + url + "\n\n" + body2 + "\n\n" + signoff + "\n" + signature
  override val html =
    <html lang={messages.lang.code}>
      <head>
        <title>{subject}</title>
      </head>
      <body>
        <p>{body1}</p>
        <p><a href={url}>{url}</a></p>
        <p>{body2}</p>
        <p>{signoff}<br/>{signature}</p>
      </body>
    </html>
}
