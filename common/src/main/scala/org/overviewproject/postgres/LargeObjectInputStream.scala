package org.overviewproject.postgres

import java.io.{IOException,InputStream}
import org.postgresql.largeobject.{LargeObject=>PGLargeObject,LargeObjectManager}
import org.postgresql.util.PSQLException
import org.postgresql.PGConnection
import scala.concurrent.blocking

import org.overviewproject.database.SlickClient

/** Reads data from a database LargeObject.
  *
  * Each call to read() opens a database connection. You should definitely wrap
  * this with a BufferedInputStream to make reads less costly.
  */
class LargeObjectInputStream(val oid: Long, slickClient: SlickClient) extends InputStream {
  private var position: Long = 0L

  override def read: Int = {
    val bytes: Array[Byte] = try {
      withLO { lo =>
        lo.seek(position.toInt)
        //lo.seek64(position)
        lo.read(1)
      }
    } catch {
      case e: PSQLException => throw new IOException(e.getMessage, e)
    }
    if (bytes.length == 1) {
      position += 1
      bytes(0)
    } else {
      -1
    }
  }

  override def read(b: Array[Byte], off: Int, len: Int): Int = {
    val n = try {
      withLO { lo =>
        lo.seek(position.toInt)
        //lo.seek64(position)
        lo.read(b, off, len)
      }
    } catch {
      case e: PSQLException => throw new IOException(e.getMessage, e)
    }
    if (n == 0) {
      -1
    } else {
      position += n
      n
    }
  }

  private def withLO[A](block: PGLargeObject => A): A = blocking {
    slickClient.blockingDb { session =>
      slickClient.withTransaction(session) {
        val pgConnection = session.conn.unwrap(classOf[PGConnection])
        val loManager = pgConnection.getLargeObjectAPI()
        val lo = loManager.open(oid, LargeObjectManager.READ)
        block(lo)
      }
    }
  }
}
