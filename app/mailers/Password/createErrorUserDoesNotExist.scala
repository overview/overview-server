package mailers.Password

import play.api.i18n.Messages

import mailers.Mailer

case class createErrorUserDoesNotExist(val email: String, val resetPasswordFormUrl: String)(implicit val messages: Messages) extends Mailer {
  private val m = views.Magic.scopedMessages("mailers.Password.createErrorUserDoesNotExist")

  private val intro = m("intro")
  private val body = m("body")
  private val case1 = m("case1")
  private val case2 = m("case2")
  private val signoff = m("signoff")
  private val signature = m("signature")

  override val recipients = Seq(email)
  override val subject = m("subject")
  override val text = Seq(intro, body, case1, case2, resetPasswordFormUrl, signoff + "\n" + signature).mkString("\n\n")
  override val html =
    <html lang={messages.lang.code}>
      <head>
        <title>{subject}</title>
      </head>
      <body>
        <p>{intro}</p>
        <p>{body}</p>
        <p>{case1}</p>
        <p>{case2}</p>
        <p><a href={resetPasswordFormUrl}>{resetPasswordFormUrl}</a></p>
        <p>{signoff}<br/>{signature}</p>
      </body>
    </html>
}
