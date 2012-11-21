package csv

import org.specs2.mutable.Specification
import java.io.StringReader
import org.specs2.specification.Scope

class CsvImportSourceSpec extends Specification {

  "CsvImportSource" should {

    trait CsvImportContext extends Scope {
      def input: String
      val reader = new StringReader(input)
      val csvImportSource = new CsvImportSource(reader)
    }

    trait ValidInput extends CsvImportContext {
      def input = """|id,text,stuff
                     |0, this is line0, stuff0
                     |1, this is line1, stuff1
                     |2, this is line2, stuff2""".stripMargin

    }

    trait ValidInputWithUpperCaseHeader extends CsvImportContext {
      def input = """|ID,TEXT,STUFF
                     |0, this is line0, stuff0
                     |1, this is line1, stuff1
                     |2, this is line2, stuff2""".stripMargin

    }

    trait MissingHeader extends CsvImportContext {
      def input = """|0, this is line0, stuff0
                     |1, this is line1, stuff1""".stripMargin
    }

    trait MissingTextColumn extends CsvImportContext {
      def input = """|id,text,stuff
                     |0, this is line0, stuff0
                     |Messed up Row
                     |2, this is line2, stuff2""".stripMargin

    }
    "skip the first line of column headers" in new ValidInput {
      val numDocuments = csvImportSource.size

      numDocuments must beEqualTo(3)
    }

    "find the text column" in new ValidInput {
      val expectedText: Seq[String] = Seq.tabulate(3) { "this is line" + _ }
      val text = csvImportSource.map(_.text.trim)
      text must be equalTo (expectedText)
    }

    "fail if no text header found" in new MissingHeader {
      csvImportSource.foreach(println) must throwA[Exception]
    }

    "Skip rows with not enough columns" in new MissingTextColumn {
      csvImportSource.size must beEqualTo(2)
    }
    
    "handle uppercase header" in new ValidInputWithUpperCaseHeader {
      csvImportSource.foreach(println) must not(throwA[Exception])
    }
  }
}