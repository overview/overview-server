package com.overviewdocs.util

import java.nio.file.{Files,Path}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.{Await,ExecutionContext,Future,blocking}
import scala.concurrent.duration.Duration

class TempFilesSpec extends Specification {
  sequential

  type EE = ExecutionEnv

  private def await[A](f: => Future[A]): A = blocking(Await.result(f, Duration.Inf))

  "withTempFileWithSuffix()" should {
    "create a tempfile" in { implicit ee: EE =>
      def asyncExists(path: Path): Future[Boolean] = Future(Files.exists(path))

      TempFiles.withTempFileWithSuffix(".foo", asyncExists) must beTrue.await
    }

    "delete the tempfile" in { implicit ee: EE =>
      var thePath: Option[Path] = None
      await(TempFiles.withTempFileWithSuffix(".foo", { path => thePath = Some(path); Future.unit }))
      Files.exists(thePath.get) must beFalse
    }
  }

  "filenameToSuffix()" should {
    def test(filename: String, suffix: String) = TempFiles.filenameToSuffix(filename) must beEqualTo(suffix)

    "return a suffix" in test("foo.doc", ".doc")
    "return no suffix" in test("foo", "")
    "avoid a trailing `.`" in test("foo.doc.", "")
    "return a double-suffix" in test("foo.doc.xls", ".doc.xls")
    "return a suffix that is the filename" in test(".doc", ".doc")
    "return a suffix after a double-dot" in test("foo..doc", ".doc")
    "not return a suffix with a double-dot within it" in test("foo.doc..xls", ".xls")
    "ignore dots in path names" in test("foo.bar/bar.baz", ".baz")
    "ignore spaces in suffixes" in test("foo. doc", "")
  }
}
