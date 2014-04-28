package mailers.User

import play.api.i18n.Lang
import play.api.mvc.RequestHeader
import play.api.Play
import play.api.Play.current
import mailers.Mailer
import models.{ConfirmationRequest, OverviewUser}
import scala.xml.Unparsed

case class create(val user: OverviewUser with ConfirmationRequest)(implicit val lang: Lang, val request: RequestHeader) extends Mailer {
  private val m = views.Magic.scopedMessages("mailers.User.create")

  private val url = controllers.routes.ConfirmationController.show(user.confirmationToken).absoluteURL() 
  private val contactUrl = Play.configuration.getString("overview.contact_url").getOrElse(throw new Exception("overview.contact_url not configured"))

  private val body1 = m("body1")
  private val body2 = Unparsed(m("body2_html", contactUrl))
  private val signoff = m("signoff")
  private val signature = m("signature")

  override val recipients = Seq(user.email)
  override val subject = m("subject")
  override val text = body1 + "\n\n" + url + "\n\n" + body2 + "\n\n" + signoff + "\n" + signature
  override val html =
    <html lang={lang.code}>
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
