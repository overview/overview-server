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
      def input = """|text,stuff
                     |this is line0, stuff0
                     |this is line1, stuff1
                     |this is line2, stuff2""".stripMargin
    }

    trait ValidInputWithId extends CsvImportContext {
      def input = """|text,stuff,id
                     |this is line0,stuff0,0
                     |this is line1,stuff1,1
                     |this is line2,stuff2,2""".stripMargin
    }

    trait ValidInputWithUpperCaseHeader extends CsvImportContext {
      def input = """|TEXT,STUFF
                     |this is line0, stuff0
                     |this is line1, stuff1
                     |this is line2, stuff2""".stripMargin

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

    trait MissingIdColumn extends CsvImportContext {
      def input = """|text,stuff,id
                     |this is line0,stuff0,
                     |this is line1,stuff1,1
                     |this is line2,stuff2,2""".stripMargin
    }

    trait IgnoredEscape extends CsvImportContext {
      def input = """|text,id
                     |some text\"aa\"bb,34""".stripMargin
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

    "find id column" in new ValidInputWithId {
      val expectedIds: Seq[Option[String]] = Seq.tabulate(3)(n => Some(n.toString))
      val ids = csvImportSource.map(_.suppliedId)
      ids must be equalTo (expectedIds)
    }

    "leave id empty no header found" in new ValidInput {
      val ids = csvImportSource.map(_.suppliedId)
      ids.head must beNone
    }
    
    "leave id empty for rows with no id set" in new MissingIdColumn {
      val ids = csvImportSource.map(_.suppliedId)
      ids.head must beNone
    }
    
    "fail if no text header found" in new MissingHeader {
      csvImportSource.foreach(println) must throwA[Exception]
    }

    "Skip rows with not enough columns" in new MissingTextColumn {
      csvImportSource.size must beEqualTo(2)
    }

    "handle uppercase header" in new ValidInputWithUpperCaseHeader {
      csvImportSource.foreach(_.text) must not(throwA[Exception])
    }
    
    "don't interpret \\ as escape" in new IgnoredEscape {
      val doc = csvImportSource.head
      doc.text must be equalTo("""some text\"aa\"bb""")
      
      doc.suppliedId must beSome.like { case s => s must be equalTo("34") }
    }
  }
}