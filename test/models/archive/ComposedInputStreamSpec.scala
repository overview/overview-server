package models.archive

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import java.io.ByteArrayInputStream
import java.io.InputStream

class ComposedInputStreamSpec extends Specification {

  "ComposedInputStream" should {

    "read empty stream" in new EmptyStreamContext {
      composedStream.read must be equalTo -1
    }

    "read(buffer) empty stream" in new EmptyStreamContext {
      val buffer = new Array[Byte](128)

      composedStream.read(buffer) must be equalTo -1
    }

    "read(buffer, offset, length) empty stream" in new EmptyStreamContext {
      val buffer = new Array[Byte](128)

      composedStream.read(buffer, 5, 20) must be equalTo -1
    }

    "read single stream" in new SingleStreamContext {
      readStream(composedStream) must be equalTo streamData
    }

    "read(buffer) single stream" in new SingleStreamContext {
      composedStream.read(buffer) must be equalTo numberOfBytes
      buffer must be equalTo streamData

      composedStream.read(buffer) must be equalTo -1
    }

    "read(buffer, offset, length) single stream" in new SingleStreamContext {
      val offset = 1
      val length = 2

      composedStream.read(buffer, offset, length) must be equalTo length

      buffer.slice(offset, offset + length) must be equalTo streamData.take(length)
    }

    "read multiple streams" in new MultipleStreamContext {
      val output = readStream(composedStream)

      output must be equalTo streamData
    }

    "read(buffer) multiple streams" in new MultipleStreamContext {
      def readWithBuffer: Array[Byte] = {
        val n = composedStream.read(buffer)
        if (n != -1) buffer.take(n)
        else Array.empty
      }

      val output = Stream.continually(readWithBuffer).takeWhile(_.nonEmpty).flatten.toArray

      output must be equalTo streamData
    }

    "read(buffer, offset, length) from multiple streams" in new MultipleStreamContext {
      def readNextChunk(offset: Int): Int = {
        val bytesRead = composedStream.read(buffer, offset, numberOfBytes)

        if (bytesRead == -1) 0
        else bytesRead + readNextChunk(offset + bytesRead)
      }

      readNextChunk(0) must be equalTo numberOfBytes
      buffer.take(numberOfBytes) must be equalTo streamData
    }

    "not support mark" in new MultipleStreamContext {
      composedStream.markSupported must beFalse
    }

    "create streams when they are needed" in {
      var secondStreamGenerated = false
      
      def firstStream() = new ByteArrayInputStream(Array[Byte](1, 2, 3))
      
      def secondStream() = {
        secondStreamGenerated = true
      new ByteArrayInputStream(Array[Byte](4, 5, 6))  
      }
      
      val composedStream = new TestComposedInputStream(firstStream, secondStream)
      
      composedStream.skip(3)
      
      secondStreamGenerated must beFalse 
      
      composedStream.skip(3)
      
      secondStreamGenerated must beTrue
    }

  }
}

trait StreamReader {
  def readStream(stream: InputStream): Array[Byte] = {
    val readValues = Stream.continually(stream.read).takeWhile(_ != -1)
    readValues.map(_.toByte).toArray
  }
}
trait EmptyStreamContext extends Scope {
  def emptyStream() = new ByteArrayInputStream(Array.empty)

  val composedStream = new TestComposedInputStream(emptyStream)
}

trait SingleStreamContext extends Scope with StreamReader {
  val numberOfBytes = 5
  val buffer = new Array[Byte](numberOfBytes)

  val streamData = (1 to numberOfBytes).toArray.map(_.toByte)

  def singleStream() = new ByteArrayInputStream(streamData)

  val composedStream = new TestComposedInputStream(singleStream)
}

trait MultipleStreamContext extends Scope with StreamReader {
  val numberOfBytes = 128
  val buffer = new Array[Byte](2 * numberOfBytes)

  val streamData = (1 to numberOfBytes).toArray.map(_.toByte)

  val streams = streamData.grouped(50).map(new ByteArrayInputStream(_)).toSeq
  val streamGenerators = streams.map(() => _)

  val composedStream = new TestComposedInputStream(streamGenerators: _*)

}

class TestComposedInputStream(streamGenerators: () => InputStream*) extends ComposedInputStream {
  override protected var subStreamGenerators: List[() => InputStream] = streamGenerators.toList

}