package mailers

import com.typesafe.plugin.{use,MailerPlugin}
import scala.xml.Node

trait Mailer {
  private val WrapWidth = 72

  val subject: String
  val recipients: Seq[String]
  val text: String
  val html: Node
  val from: Seq[String] = Seq(sys.props.get("mail.from").getOrElse(throw new Exception("You must set the 'mail.from' property to send mail.")))

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
    import play.api.Play.current

    val mail = use[MailerPlugin].email
    mail.setSubject(subject)
    recipients.map(mail.addRecipient(_))
    from.map(mail.addFrom(_))
    mail.send(wordWrappedText, htmlString)
  }
}
