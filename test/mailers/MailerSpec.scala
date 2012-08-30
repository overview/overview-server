package mailers

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Play.{start,stop}
import play.api.test.{FakeApplication,FakeRequest}

class MailerSpec extends Specification {
  step(start(FakeApplication()))

  trait OurContext extends Scope {
    var mailerSubject = "Subject"
    var mailerRecipients = Seq("recipient@example.org")
    var mailerText = "Text"
    var mailerHtml = <html><head><title>Title</title></head><body>Body</body></html>

    lazy val mailer = new Mailer {
      override val subject = mailerSubject
      override val recipients = mailerRecipients
      override val text = mailerText
      override val html = mailerHtml
    }
  }

  "Mailer" should {
    "add a DOCTYPE to html" in new OurContext {
      mailer.htmlString.must(beEqualTo("<!DOCTYPE html>\n<html><head><title>Title</title></head><body>Body</body></html>"))
    }

    "word-wrap the plain text" in new OurContext {
      mailerText = "a string that is longer than seventy eight characters but has spaces in between words and only has words which are shorter than seventy eight characters."
      mailer.wordWrappedText.must(beEqualTo("a string that is longer than seventy eight characters but has spaces in\nbetween words and only has words which are shorter than seventy eight\ncharacters."))
    }

    "not wrap long URLs" in new OurContext {
      mailerText = "here is a url:\n\nhttps://example.org/long/url/12345678901234567890123456789012345678901234567890123456789012345678901234567890\n\nEnjoy!"
      mailer.wordWrappedText.must(beEqualTo(mailerText))
    }
    
    "read from from config file" in new OurContext {
      mailer.from must contain("sender@example.org")
    }
  }

  step(stop)
}
