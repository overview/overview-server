package models.export.format

import akka.stream.scaladsl.Source
import akka.util.ByteString
import java.io.{ByteArrayOutputStream,FilterOutputStream,OutputStream}
import play.api.libs.iteratee.{Enumeratee,Enumerator}

import models.export.rows.Rows

/** Provides a java.io.OutputStream-friendly streaming format.
  *
  * Implementors will decide upon a Context class (for instance,
  * java.io.ZipOutputStream) and then write to it when prompted -- and *only*
  * when prompted.
  */
trait WriteBasedFormat[Context] { self: Format =>
  import WriteBasedFormat.Step

  /** Creates the context you'll use to write things.
    *
    * For instance, the simplest context is just the OutputStream itself:
    *
    *   override def createContext(sink: OutputStream) = sink
    *   override def writeBegin(context: OutputStream) = context.write(..)
    *   ...
    *
    * But more interesting would be to write to a zipfile:
    *
    *   override def createContext(sink: OutputStream) = new ZipOutputStream(sink)
    *   override def writeBegin(context: ZipOutputStream) = context.write(...)
    *
    * And you may prefer to use a context that tracks state, too.
    */
  protected def createContext(sink: OutputStream): Context

  /** Writes the first few bytes of the output to context. */
  protected def writeBegin(context: Context): Unit

  /** Writes the header row to context. */
  protected def writeHeaders(headers: Array[String], context: Context): Unit

  /** Writes a row to context. */
  protected def writeRow(row: Array[String], context: Context): Unit

  /** Writes the end of the file to context.
    *
    * You must call any `flush` or `close` methods that are necessary to clear
    * any buffers here.
    */
  protected def writeEnd(context: Context): Unit

  /** Provides an OutputStream for implementations and returns a Source.
    *
    * Implementations should implement the writeBegin(), writeHeaders(),
    * writeRow() and writeEnd() methods, which will all call write() on some
    * OutputStream that eventually writes to the `sink` passed to
    * createContext(). This method will call them those methods at the correct
    * moments and re-route their output to the returned enumerator.
    */
  override def byteSource(rows: Rows): Source[ByteString, akka.NotUsed] = {
    val sink = new ByteArrayOutputStream
    val context = createContext(sink)

    val steps: Source[Step, akka.NotUsed] = Source.single(Step.Begin)
      .concat(Source.single(Step.Headers(rows.headers)))
      .concat(rows.rows.map(Step.Row))
      .concat(Source.single(Step.End))

    steps
      .map { step => stepToBytes(step, sink, context) }
      .filter(_.nonEmpty) // an empty chunk ends an HTTP Chunked transfer
  }

  /** Calls writeXXX(xxx, context) and then returns the bytes that were
    * written to context.
    */
  private def stepToBytes(step: Step, sink: ByteArrayOutputStream, context: Context): ByteString = {
    step match {
      case Step.Begin => writeBegin(context)
      case Step.Headers(headers) => writeHeaders(headers, context)
      case Step.Row(row) => writeRow(row, context)
      case Step.End => writeEnd(context)
    }

    val ret = sink.toByteArray
    sink.reset
    ByteString(ret)
  }
}

object WriteBasedFormat {
  sealed trait Step
  object Step {
    case object Begin extends Step
    case class Headers(headers: Array[String]) extends Step
    case class Row(row: Array[String]) extends Step
    case object End extends Step
  }
}
