package models.export.format

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

  protected implicit val executionContext = play.api.libs.concurrent.Execution.defaultContext

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

  /** Provides an OutputStream for implementations and returns an Enumerator.
    *
    * Implementations should implement the writeBegin(), writeHeaders(),
    * writeRow() and writeEnd() methods, which will all call write() on some
    * OutputStream that eventually writes to the `sink` passed to
    * createContext(). This method will call them those methods at the correct
    * moments and re-route their output to the returned enumerator.
    */
  override def bytes(rows: Rows): Enumerator[Array[Byte]] = {
    val sink = new ByteArrayOutputStream
    val context = createContext(sink)

    val steps = Enumerator[Step](Step.Begin, Step.Headers(rows.headers))
      .andThen(rows.rows.map(Step.Row))
      .andThen(Enumerator(Step.End))
    steps.through(stepper(sink, context))
  }

  private def stepper(sink: ByteArrayOutputStream, context: Context): Enumeratee[Step,Array[Byte]] = {
    Enumeratee.map(step => stepToBytes(step, sink, context))
  }

  /** Calls writeXXX(xxx, context) and then returns the bytes that were
    * written to context.
    */
  private def stepToBytes(step: Step, sink: ByteArrayOutputStream, context: Context) = {
    step match {
      case Step.Begin => writeBegin(context)
      case Step.Headers(headers) => writeHeaders(headers, context)
      case Step.Row(row) => writeRow(row, context)
      case Step.End => writeEnd(context)
    }

    val ret = sink.toByteArray
    sink.reset
    ret
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
