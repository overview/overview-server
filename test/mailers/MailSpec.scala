package mailers

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class MailSpec extends Specification {
  trait OurContext extends Scope {
    var mailSubject = "Subject"
    var mailRecipients = Seq("recipient@example.org")
    var mailText = "Text"
    var mailHtml = <html><head><title>Title</title></head><body>Body</body></html>

    lazy val mail = new Mail {
      override val subject = mailSubject
      override val recipients = mailRecipients
      override val text = mailText
      override val html = mailHtml
    }
  }

  "Mailer" should {
    "add a DOCTYPE to html" in new OurContext {
      mail.htmlString.must(beEqualTo("<!DOCTYPE html>\n<html><head><title>Title</title></head><body>Body</body></html>"))
    }

    "word-wrap the plain text" in new OurContext {
      mailText = "a string that is longer than seventy eight characters but has spaces in between words and only has words which are shorter than seventy eight characters."
      mail.wordWrappedText.must(beEqualTo("a string that is longer than seventy eight characters but has spaces in\nbetween words and only has words which are shorter than seventy eight\ncharacters."))
    }

    "not wrap long URLs" in new OurContext {
      mailText = "here is a url:\n\nhttps://example.org/long/url/12345678901234567890123456789012345678901234567890123456789012345678901234567890\n\nEnjoy!"
      mail.wordWrappedText.must(beEqualTo(mailText))
    }
  }
}
