package mailers

import play.api.libs.mailer.{Email,SMTPConfiguration,SMTPMailer}
import play.api.Play
import scala.xml.Node

trait Mailer {
  private val WrapWidth = 72

  val subject: String
  val recipients: Seq[String]
  val text: String
  val html: Node

  private def wordWrapLine(line: String) : String = {
    if (line.length <= WrapWidth) {
      line.trim()
    } else {
      val idealIndex = line.lastIndexOf(' ', WrapWidth)
      val index = if (idealIndex != -1) idealIndex else line.indexOf(' ', WrapWidth)
      if (index <= 0) {
        line.trim()
      } else {
        line.substring(0, index).trim() + "\n" + wordWrapLine(line.substring(index))
      }
    }
  }

  lazy val wordWrappedText = text.lines.map(wordWrapLine).mkString("\n")

  lazy val htmlString = "<!DOCTYPE html>\n" + html.buildString(true)

  def send = {
    val email = Email(
      subject,
      Mailer.from,
      recipients,
      bodyText=Some(wordWrappedText),
      bodyHtml=Some(htmlString),
      charset=Some("utf-8")
    )

    Mailer.send(email)
  }
}

object Mailer {
  def from: String = Play.current.configuration.getString("mail.from").get

  // Play suggests we use a module. But the module doesn't behave the way we
  // want; and it seems a waste of effort to me, anyway.
  def send(data: Email): String = {
    val c = Play.current.configuration.getConfig("play.mailer").get.underlying

    val smtpConfiguration = SMTPConfiguration(
      host=c.getString("host"),
      port=c.getInt("port"),
      ssl=c.getBoolean("ssl"),
      tls=c.getBoolean("tls"),
      user=Some(c.getString("user")).filter(_.nonEmpty),
      password=Some(c.getString("password")).filter(_.nonEmpty),
      debugMode=false,
      timeout=None,
      connectionTimeout=None,
      mock=c.getString("host") == ""
    )

    val mailer = new SMTPMailer(smtpConfiguration)
    mailer.send(data)
  }
}
