package org.overviewproject.jobhandler.filegroup.task

import java.io.InputStream
import java.util.UUID

/** Converts an [[InputStream]] to a PDF and calls a method with it.
  */
trait DocumentConverter {
  /** Converts the given `inputStream` into a PDF [[InputStream]].
    *
    * The PDF input stream will close automatically after `f` returns.
    *
    * @param guid A unique id for creating temporary files.
    * @param filename The filename of the input file.
    * @param inputStream The document data to be converted.
    * @param f A function that will be passed the PDF [[InputStream]].
    * @tparam T the return type of `f`
    *
    * @returns the return value of `f`
    * @throws Exception if `f` throws an exception
    */
  def withStreamAsPdf[T](guid: UUID, filename: String, inputStream: InputStream)(f: InputStream => T): T
}
