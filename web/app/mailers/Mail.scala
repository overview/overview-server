package mailers

import play.api.libs.mailer.Email
import scala.xml.Node

trait Mail {
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

  def toEmail(from: String): Email = Email(
    subject,
    from,
    recipients,
    bodyText=Some(wordWrappedText),
    bodyHtml=Some(htmlString),
    charset=Some("utf-8")
  )
}
