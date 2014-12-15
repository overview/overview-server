package org.overviewproject.blobstorage

import java.io.InputStream
import org.postgresql.PGConnection
import org.postgresql.util.PSQLException
import play.api.libs.iteratee.Enumerator
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future,blocking}
import scala.slick.jdbc.JdbcBackend.Session

import org.overviewproject.database.DB
import org.overviewproject.postgres.{LO,LargeObject}

trait PgLoStrategy extends BlobStorageStrategy {

  /** Size of each Array[Byte] we push.
    *
    * Larger causes blocking operations to block longer. Smaller causes more
    * database connections.
    */
  protected val BufferSize = 4 * 1024 * 1024 // 4MB, chosen at random

  /** Execute code with a PGConnection. */
  protected def withPgConnection[A](code: PGConnection => A): A
  /** Execute code in Slick. */
  protected def withSlickSession[A](code: Session => A): A

  /** Execute code with the given LargeObject. */
  private def withLargeObject[A](loid: Long)(f: LargeObject => A): A = {
    blocking {
      withPgConnection { pgConnection =>
        LO.withLargeObject(loid)({ lo => f(lo) })(pgConnection)
      }
    }
  }

  /** Execute code with the given newly-created LargeObject. */
  private def withNewLargeObject[A](f: LargeObject => A): A = {
    blocking {
      withPgConnection { pgConnection =>
        LO.withLargeObject({ lo => f(lo) })(pgConnection)
      }
    }
  }

  private def locationToOid(location: String): Long = {
    if (!location.startsWith("pglo:")) throw new IllegalArgumentException("Invalid prefix on location: " + location)
    location.substring(5).toLong
  }

  /** Throws an exception iff we cannot access the given large object. */
  private def testLargeObject(loid: Long): Unit = withLargeObject(loid) { lo => () }

  /** Returns an Enumerator over the given large object. */
  private def enumerateLargeObject(loid: Long): Enumerator[Array[Byte]] = {
    val buffer = new Array[Byte](BufferSize)

    // Reads [position,position+BufferSize) bytes.
    //
    // Returns Some(newPosition, bytes) if there are more bytes; otherwise
    // returns None
    def continue(position: Int): Future[Option[(Int,Array[Byte])]] = {
      Future(withLargeObject(loid) { lo =>
        lo.seek(position)
        val nBytes = lo.read(buffer, 0, BufferSize)
        val newPosition = position + nBytes
        nBytes match {
          case 0 => None
          case _ => Some(newPosition, buffer.take(nBytes)) // always a copy
        }
      })
    }

    Enumerator.unfoldM(0)(continue _)
  }

  override def get(location: String): Future[Enumerator[Array[Byte]]] = {
    val loid = locationToOid(location)
    Future {
      testLargeObject(loid); // fail fast
      enumerateLargeObject(loid);
    }
  }

  override def delete(location: String): Future[Unit] = deleteMany(Seq(location))

  override def deleteMany(locations: Seq[String]): Future[Unit] = {
    // LO.delete() puts the connection in an inconsistent state if the loid is
    // invalid. But our contract states the loid may be invalid. So we can't
    // use LO.delete() ... or lo_unlink() even. We need some nifty SQL.

    val loids = locations.map(locationToOid _)

    Future(blocking {
      import org.overviewproject.database.Slick.simple._
      import scala.slick.jdbc.StaticQuery

      val q = s"""
        DO $$$$
        DECLARE
          loids BIGINT[] := ARRAY[${loids.mkString(",")}];
          loid BIGINT;
        BEGIN
          FOREACH loid IN ARRAY loids LOOP
            BEGIN
              PERFORM lo_unlink(loid);
            EXCEPTION
              WHEN undefined_object THEN NULL;
            END;
          END LOOP;
        END$$$$;
      """

      withSlickSession { session =>
        StaticQuery.updateNA(q).apply().execute(session)
      }
    })
  }

  private def copy(inputStream: InputStream, lo: LargeObject, nBytes: Long) = {
    val buffer = new Array[Byte](BufferSize)
    var remaining = nBytes
    while (remaining > 0) {
      val bytesRead = inputStream.read(buffer, 0, BufferSize)
      lo.add(buffer.take(bytesRead))
      remaining -= bytesRead
    }
  }

  override def create(locationPrefix: String, inputStream: InputStream, nBytes: Long): Future[String] = {
    if (locationPrefix != "pglo") throw new IllegalArgumentException("locationPrefix must be pglo; got: " + locationPrefix);
    Future(withNewLargeObject { lo: LargeObject =>
      copy(inputStream, lo, nBytes)
      "pglo:" + lo.oid
    })
  }
}

object PgLoStrategy extends PgLoStrategy {
  override protected def withPgConnection[A](f: PGConnection => A): A = {
    DB.withConnection { connection =>
      val pgConnection = DB.pgConnection(connection)
      f(pgConnection)
    }
  }

  override protected def withSlickSession[A](f: Session => A): A = {
    DB.withConnection { connection =>
      val session = DB.slickSession(connection)
      f(session)
    }
  }
}
