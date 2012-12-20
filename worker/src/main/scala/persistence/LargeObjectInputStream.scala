package persistence

import scala.util.control.Exception._
import java.io.InputStream
import org.overviewproject.database.Database
import org.overviewproject.postgres.LO
import org.overviewproject.database.DB
import org.postgresql.util.PSQLException
import java.io.IOException

class LargeObjectInputStream(oid: Long, bufferSize: Int = 8192) extends InputStream {
  private var ReadWhenClosedExceptionMessage = "Attempting to read from closed stream"

  private val buffer = new Array[Byte](bufferSize)
  private var largeObjectPosition: Int = 0
  private var bufferPosition: Int = bufferSize
  private var bufferEnd: Int = bufferSize
  private var isOpen: Boolean = true

  def read(): Int = {
    ifOpen {
      convertPsqlException(refreshBuffer())
      readNextFromBuffer()
    }
    

  }

  override def read(outBuffer: Array[Byte], offset: Int, len: Int): Int = {
    ifOpen {
      convertPsqlException(refreshBuffer())

      if (bufferPosition + len <= bufferEnd) {
        Array.copy(buffer, bufferPosition, outBuffer, offset, len)
        bufferPosition += len
        len
      } else {
        val available = bufferEnd - bufferPosition
        if (available != 0) {
          Array.copy(buffer, bufferPosition, outBuffer, offset, available)
          bufferPosition = bufferEnd
          
          refreshBuffer()
          
          if (bufferEnd == 0) available
          else available + read(outBuffer, offset + available, len - available)
        } else -1
      }
    }
  }

  override def close() { isOpen = false }

  private def readNextFromBuffer(): Int = {
    val b =
      if (bufferPosition < bufferEnd) toUnsignedInt(buffer(bufferPosition))
      else -1

    bufferPosition += 1

    b
  }

  private def toUnsignedInt(b: Byte): Int = 0xff & b

  private def ifOpen[A](f: => A) = {
    if (isOpen) f
    else throw new IOException(ReadWhenClosedExceptionMessage)
  }
    
  private def convertPsqlException(f: => Unit) { handling(classOf[PSQLException]) by (e => throw new IOException(e.getMessage)) apply f }
  
  private def refreshBuffer() {
    if (bufferPosition >= bufferSize) Database.inTransaction {
      implicit val pgc = DB.pgConnection(Database.currentConnection)

      LO.withLargeObject(oid) { largeObject =>
        largeObject.seek(largeObjectPosition)
        val bytesRead = largeObject.read(buffer, 0, bufferSize)
        bufferEnd = bytesRead
        largeObjectPosition += bytesRead
        bufferPosition = 0
      }
    }
  }
}