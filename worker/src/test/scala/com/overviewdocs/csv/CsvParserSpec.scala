package com.overviewdocs.csv

import scala.collection.mutable

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class CsvParserSpec extends Specification {
  trait BaseScope extends Scope {
    val parser = new CsvParser()
    def getParsedRows: Seq[Seq[String]] = parser.getParsedRows.map(_.toSeq)
  }

  "parse a row" in new BaseScope {
    parser.write("foo".toCharArray)
    parser.end
    getParsedRows must beEqualTo(Seq(Seq("foo")))
  }

  "parse mid-Array" in new BaseScope {
    parser.write("XXXfooXXX".toCharArray, 3, 3)
    parser.end
    getParsedRows must beEqualTo(Seq(Seq("foo")))
  }

  "ignore empty rows" in new BaseScope {
    parser.write("\n\n".toCharArray)
    parser.end
    getParsedRows must beEqualTo(Seq())
  }

  "separate values by comma" in new BaseScope {
    parser.write("foo,bar\n".toCharArray)
    getParsedRows must beEqualTo(Seq(Seq("foo", "bar")))
  }

  "return an empty string at the end of a row" in new BaseScope {
    parser.write("foo,\n".toCharArray)
    getParsedRows must beEqualTo(Seq(Seq("foo", "")))
  }

  "return an empty string at the start of a row" in new BaseScope {
    parser.write(",foo\n".toCharArray)
    getParsedRows must beEqualTo(Seq(Seq("", "foo")))
  }

  "return two empty strings on a single comma" in new BaseScope {
    parser.write(",\n".toCharArray)
    getParsedRows must beEqualTo(Seq(Seq("", "")))
  }

  "return an empty string at the middle of a row" in new BaseScope {
    parser.write(",,\n".toCharArray)
    getParsedRows must beEqualTo(Seq(Seq("", "", "")))
  }

  "handle quotes" in new BaseScope {
    parser.write("""foo,"bar",baz""".toCharArray)
    parser.end
    getParsedRows must beEqualTo(Seq(Seq("foo", "bar", "baz")))
  }

  "handle double-quotes" in new BaseScope {
    parser.write("""foo,"ba""r",baz""".toCharArray)
    parser.end
    getParsedRows must beEqualTo(Seq(Seq("foo", "ba\"r", "baz")))
  }

  "handle newlines in quotes" in new BaseScope {
    parser.write("foo,\"bar\r\nbaz\"".toCharArray)
    parser.end
    getParsedRows must beEqualTo(Seq(Seq("foo", "bar\r\nbaz")))
  }

  "return something when quote comes mid-value" in new BaseScope {
    parser.write("last valid row\nfoo,bar\"baz".toCharArray)
    parser.end
    getParsedRows must beEqualTo(Seq(Seq("last valid row")))
  }

  "handle stuff coming in mid-write" in new BaseScope {
    parser.write("foo,ba".toCharArray)
    parser.write("r,baz".toCharArray)
    parser.end
    getParsedRows must beEqualTo(Seq(Seq("foo", "bar", "baz")))
  }

  "parse an empty string" in new BaseScope {
    parser.write("".toCharArray)
    parser.end
    getParsedRows must beEqualTo(Seq())
  }

  "parse quoted first value" in new BaseScope {
    parser.write("\"foo\",".toCharArray)
    parser.end
    getParsedRows must beEqualTo(Seq(Seq("foo", "")))
  }

  "parse quoted last value" in new BaseScope {
    parser.write(",\"foo\"".toCharArray)
    parser.end
    getParsedRows must beEqualTo(Seq(Seq("", "foo")))
  }

  "clear parsed rows" in new BaseScope {
    parser.write("foo,bar\nbaz,moo\n".toCharArray)
    getParsedRows must beEqualTo(Seq(Seq("foo", "bar"), Seq("baz", "moo")))
    parser.clearParsedRows
    getParsedRows must beEqualTo(Seq())
    parser.write("mar,maz\n".toCharArray)
    getParsedRows must beEqualTo(Seq(Seq("mar", "maz")))
  }

  "keep current row when clearing parsed ones" in new BaseScope {
    parser.write("foo,".toCharArray)
    parser.clearParsedRows
    parser.write("bar\n".toCharArray)
    getParsedRows must beEqualTo(Seq(Seq("foo", "bar")))
  }

  "return true on valid EOF" in new BaseScope {
    parser.write("foo,".toCharArray)
    parser.end
    parser.isFullyParsed must beTrue
  }

  "return false when in quotes" in new BaseScope {
    parser.write("\"".toCharArray)
    parser.end
    parser.isFullyParsed must beFalse
  }

  "work when multi-byte characters are split across writes" in new BaseScope {
    val chars = "one🍠\ntwo🍠".toCharArray // 🍠 is 2 chars
    chars.grouped(1).foreach(parser.write _)
    parser.end
    getParsedRows.flatten must beEqualTo(Seq("one🍠", "two🍠"))
  }
}
