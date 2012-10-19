package mailers.Password

import play.api.i18n.Lang
import play.api.mvc.RequestHeader

import mailers.Mailer
import models.{OverviewUser,ResetPasswordRequest}

case class create(val user: OverviewUser with ResetPasswordRequest)(implicit val lang: Lang, val request: RequestHeader) extends Mailer {
  private val m = views.Magic.scopedMessages("mailers.Password.create")

  private val url = controllers.routes.PasswordController.edit(user.resetPasswordToken).absoluteURL() 
  private val body = m("body")
  private val signoff = m("signoff")
  private val case1 = m("case1")
  private val case2 = m("case2")
  private val signature = m("signature")

  override val recipients = Seq(user.email)
  override val subject = m("subject")
  override val text = Seq(body, case1, case2, url, signoff + "\n" + signature).mkString("\n\n")
  override val html =
    <html lang={lang.code}>
      <head>
        <title>{subject}</title>
      </head>
      <body>
        <p>{body}</p>
        <p>{case1}</p>
        <p>{case2}</p>
        <p><a href={url}>{url}</a></p>
        <p>{signoff}<br/>{signature}</p>
      </body>
    </html>
}
