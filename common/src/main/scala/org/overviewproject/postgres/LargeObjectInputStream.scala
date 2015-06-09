package org.overviewproject.postgres

import java.io.{IOException,InputStream}
import java.sql.SQLException
import scala.concurrent.ExecutionContext.Implicits.global

import org.overviewproject.database.{BlockingDatabase,LargeObject=>GoodLargeObject,LargeObjectManager}

/** Reads data from a database LargeObject.
  *
  * Each call to read() opens a database connection. You should definitely wrap
  * this with a BufferedInputStream to make reads less costly.
  */
class LargeObjectInputStream(val oid: Long, blockingDatabase: BlockingDatabase) extends InputStream {
  private var position: Long = 0L
  private val loManager = blockingDatabase.largeObjectManager

  override def read: Int = {
    val bytes = readByteArrayAndIncrementPosition(1)
    bytes.headOption.map(_.toInt).getOrElse(-1)
  }

  override def read(b: Array[Byte], off: Int, len: Int): Int = {
    val bytes = readByteArrayAndIncrementPosition(len)
    if (bytes.length == 0 && len > 0) {
      -1
    } else {
      Array.copy(bytes, 0, b, off, bytes.length)
      bytes.length
    }
  }

  private def readByteArrayAndIncrementPosition(length: Int) = {
    import org.overviewproject.database.Slick.api._
    val bytes = try {
      blockingDatabase.run((for {
        lo <- loManager.open(oid, GoodLargeObject.Mode.Read)
        _ <- lo.seek(position)
        bytes <- lo.read(length)
      } yield bytes).transactionally)
    } catch {
      case e: SQLException => throw new IOException(e.getMessage, e)
    }
    position += bytes.length
    bytes
  }
}
