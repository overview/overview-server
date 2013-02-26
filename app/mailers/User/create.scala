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
  private val helpUrl = Play.configuration.getString("overview.help_url").getOrElse(throw new Exception("overview.help_url not configured"))
  private val userForumUrl = Play.configuration.getString("overview.user_forum_url").getOrElse(throw new Exception("overview.user_forum_url not configured"))

  private val body = m("body")
  private val signoff = Unparsed(m("signoff_html", helpUrl, userForumUrl))
  private val signature = m("signature")

  override val recipients = Seq(user.email)
  override val subject = m("subject")
  override val text = body + "\n\n" + url + "\n\n" + signoff + "\n" + signature
  override val html =
    <html lang={lang.code}>
      <head>
        <title>{subject}</title>
      </head>
      <body>
        <p>{body}</p>
        <p><a href={url}>{url}</a></p>
        <p>{signoff}<br/>{signature}</p>
      </body>
    </html>
}
