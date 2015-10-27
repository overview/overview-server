package com.overviewdocs.jobhandler.filegroup.task

import java.io.InputStream
import java.nio.file.Path
import java.util.UUID
import scala.concurrent.Future

/** Converts an [[InputStream]] to a PDF and calls a method with it. */
trait DocumentConverter {
  /** Converts the given `inputStream` into a PDF [[InputStream]].
    *
    * The PDF input stream will close automatically after `f` returns.
    *
    * @param guid A unique id for creating temporary files.
    * @param inputStream The document data to be converted.
    * @param f A function that will be passed the PDF [[InputStream]] and its length.
    * @tparam T the return type of `f`
    *
    * @returns the return value of `f` 
    * @throws Exception if `f` throws an exception
    */
  def withStreamAsPdf[T](guid: UUID, inputStream: InputStream)(f: (InputStream, Long) => Future[T]): Future[T]

  /** Converts the given `file` into a PDF. */
  def convertFileToPdf(in: Path, out: Path): Future[Unit]
}
