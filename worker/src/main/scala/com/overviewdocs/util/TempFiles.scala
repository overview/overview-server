package com.overviewdocs.util

import java.nio.file.{Files,Path}
import scala.concurrent.{ExecutionContext,Future,blocking}

object TempFiles {
  private val SuffixPattern = ".*?((?:\\.\\w+)+)$".r

  /** Calls the given Future-producing function with a tempfile.
    *
    * For instance:
    *
    *
    *   val doSomethingAsync: (Path => Future[String]) = ...
    *   val result: Future[String] = TempFiles.withTempFileWithSuffix(".pdf") { path: Path =>
    *     doSomethingAsync(path)
    *   }
    */
  def withTempFileWithSuffix[A](suffix: String, f: Path => Future[A])(implicit ec: ExecutionContext) = {
    val path = Files.createTempFile("temp-file", suffix)
    f(path).andThen { case _ => blocking(Files.delete(path)) }
  }

  /** Get a filename's suffix. TODO find a better place to put this method.
    *
    * If you're going to run an external program with a tempfile as input, the
    * external program may function better if the tempfile has the correct
    * filename extension. Use this method to get a suitable extension.
    *
    * Details:
    *
    * * The suffix includes a "."
    * * If the original filename has no suffix, the return value will be ""
    * * If the original filename looks like "foo.bar.baz", the return value
    *   will be ".bar.baz"
    * * If the original filename looks like ".foo", the return value will be
    *   ".foo"
    * * If the original filename looks like "..foo", the return value will be
    *   ".foo"
    *
    * In brief: the return value matches `/(\.\w+)*$/`.
    */
  def filenameToSuffix(filename: String): String = {
    filename match {
      case SuffixPattern(s) => s
      case _ => ""
    }
  }
}
