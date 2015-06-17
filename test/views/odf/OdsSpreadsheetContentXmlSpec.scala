package views.odf

import java.lang.StringBuilder
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.iteratee.Enumerator
import scala.concurrent.Future

import models.export.rows.Rows

class OdsSpreadsheetContentXmlSpec extends Specification {
  trait SpreadsheetScope extends Scope {
    val headers : Array[String] = Array("header 1", "header 2", "header 3")
    val rows : Enumerator[Array[String]] = Enumerator(
      Array("one", "two", "three"),
      Array("four", "five", "six")
    )

    val outputBuffer = new StringBuilder()
    def write(s: String): Future[Unit] = { outputBuffer.append(s); Future.successful(()) }

    lazy val text: Future[String] = OdsSpreadsheetContentXml(Rows(headers, rows), write _)
      .map(_ => outputBuffer.toString)
  }

  "OdsSpreadsheetContentXml" should {
    "have an office:mimetype of application/vnd.oasis.opendocument.spreadsheet" in new SpreadsheetScope {
      text must contain("""office:mimetype="application/vnd.oasis.opendocument.spreadsheet"""").await
    }

    "put headers in a <text:p>" in new SpreadsheetScope {
      text must contain("<text:p>header 1</text:p>").await
    }

    "put contents in a <text:p>" in new SpreadsheetScope {
      text must contain("<text:p>five</text:p>").await
    }
  }
}
