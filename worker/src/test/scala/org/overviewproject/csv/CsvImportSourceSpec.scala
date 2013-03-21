package org.overviewproject.csv

import java.io.StringReader
import org.specs2.specification.Scope
import org.overviewproject.test.Specification

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

    trait ValidInputWithUrl extends CsvImportContext {
      def input = """|text,url,id
                     |t0,url0,id0
                     |t1,url1,id1
                     |t2,url2,id2""".stripMargin
    }

    trait ValidInputWithUpperCaseHeader extends CsvImportContext {
      def input = """|TEXT,STUFF
                     |this is line0, stuff0
                     |this is line1, stuff1
                     |this is line2, stuff2""".stripMargin
    }

    trait ValidInputWithTitle extends CsvImportContext {
      def input = """|text,title
    	               |this is line0,title""".stripMargin
    }

    trait MissingHeader extends CsvImportContext {
      def input = """|0, this is line0, stuff0
                     |1, this is line1, stuff1""".stripMargin
    }

    trait MissingIdColumn extends CsvImportContext {
      def input = """|text,stuff,id
                     |this is line0,stuff0,
                     |this is line1,stuff1,1
                     |this is line2,stuff2,2
                     |this is line3,stuff3""".stripMargin
    }

    trait IgnoredEscape extends CsvImportContext {
      def input = """|text,id
                     |some text\"aa\"bb,34""".stripMargin
    }

    trait MissingText extends CsvImportContext {
      def input = """|text,id
                     |
                     |,33
                     |
                     |
                     |,34,stuff""".stripMargin
    }
    
   trait ValidInputContentsHeader extends CsvImportContext {
      def input = """|contents,stuff
                     |this is line0, stuff0
                     |this is line1, stuff1
                     |this is line2, stuff2""".stripMargin
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

    "find the text column if labelled contents" in new ValidInputContentsHeader {
      val expectedText: Seq[String] = Seq.tabulate(3) { "this is line" + _ }
      val text = csvImportSource.map(_.text.trim)
      text must be equalTo (expectedText)
    }
    
    "find id column" in new ValidInputWithId {
      val expectedIds: Seq[Option[String]] = Seq.tabulate(3)(n => Some(n.toString))
      val ids = csvImportSource.map(_.suppliedId)
      ids must be equalTo (expectedIds)
    }

    "find title column" in new ValidInputWithTitle {
      val expectedTitle = Some("title")
      val title = csvImportSource.map(_.title).head
      title must be equalTo expectedTitle
    }

    "leave id empty no header found" in new ValidInput {
      val ids = csvImportSource.map(_.suppliedId)
      ids.head must beNone
    }

    "leave id empty for rows with no id set" in new MissingIdColumn {
      val ids = csvImportSource.map(_.suppliedId)
      ids.head must beNone
      ids.last must beNone
    }

    "fail if no text header found" in new MissingHeader {
      csvImportSource.foreach(println) must throwA[Exception]
    }

    "handle uppercase header" in new ValidInputWithUpperCaseHeader {
      csvImportSource.foreach(_.text) must not(throwA[Exception])
    }

    "don't interpret \\ as escape" in new IgnoredEscape {
      val doc = csvImportSource.head
      doc.text must be equalTo ("""some text\"aa\"bb""")

      doc.suppliedId must beSome("34")
    }

    "create documents with empty text if no text is found" in new MissingText {
      val texts = csvImportSource.map(_.text)
      texts must haveTheSameElementsAs(Seq.fill(5)(""))
    }

    "leave url empty if no header found" in new ValidInput {
      val urls = csvImportSource.map(_.url)
      urls.head must beNone
    }

    "find urls" in new ValidInputWithUrl {
      val expectedUrls: Seq[Option[String]] = Seq.tabulate(3)(n => Some("url" + n))
      val urls = csvImportSource.map(_.url)
      urls must be equalTo (expectedUrls)
    }
  }
}
