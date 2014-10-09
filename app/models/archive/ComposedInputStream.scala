package models.archive

import java.io.InputStream
import scala.collection.mutable.Queue

/**
 * Compose dynamically generated [[InputStream]]s. Subclasses provide a [[List]] of generator functions that
 * return [[InputStream]]. A generator will not be called until the previously created [[InputStream]] is empty.
 */
abstract class ComposedInputStream extends InputStream {

  override def read: Int = readCurrentStream(_.read)

  override def read(buffer: Array[Byte]): Int = readCurrentStream(_.read(buffer))

  override def read(buffer: Array[Byte], offset: Int, length: Int): Int =
    readCurrentStream(_.read(buffer, offset, length))

  /** close current stream, in case that is necessary */
  override def close: Unit = currentStreamValue.map(_.close)

  /** [[List]] of functions that return [[InputStream]]s that should be combined into one stream */
  protected var subStreamGenerators: List[() => InputStream]

  private def currentStream: Option[InputStream] =
    currentStreamValue.orElse(initializeStream)

  private var currentStreamValue: Option[InputStream] = None

  private def initializeStream: Option[InputStream] = {
    currentStreamValue = subStreamGenerators.headOption.map(_())
    currentStreamValue
  }

  private def readCurrentStream(readFn: InputStream => Int): Int = currentStream.map { s =>
    val n = readFn(s)
    if (n != -1) n
    else continueToNextStream(readFn)
  }.getOrElse(-1)

  private def continueToNextStream(readFn: InputStream => Int): Int = {
    currentStreamValue.map(_.close)
    subStreamGenerators = subStreamGenerators.tail
    initializeStream
    readCurrentStream(readFn)
  }

}