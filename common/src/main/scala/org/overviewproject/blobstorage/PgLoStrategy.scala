package org.overviewproject.blobstorage

import java.io.{IOException,InputStream}
import play.api.libs.iteratee.Enumerator
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.overviewproject.database.{HasDatabase,DatabaseProvider,LargeObject}

trait PgLoStrategy extends BlobStorageStrategy with HasDatabase {
  import databaseApi._

  /**
   * Size of each Array[Byte] we push.
   *
   * Larger causes database operations to block longer, starving other
   * operations. Smaller causes more database operations, killing throughput.
   */
  protected val BufferSize = 4 * 1024 * 1024 // 4MB, chosen at random

  /**
   * Number of large objects we delete in one go
   *
   * We don't delete them all at once, because that would be too slow.
   * (Historically, Postgres 9.1 would abort any transaction that tried to
   * delete more than 1,000 large objects.
   */
  protected val DeleteManyChunkSize = 1000

  private def locationToOid(location: String): Long = {
    if (!location.startsWith("pglo:")) throw new IllegalArgumentException("Invalid prefix on location: " + location)
    location.substring(5).toLong
  }

  /** Throws an exception iff we cannot access the given large object. */
  private def testLargeObject(loid: Long): Future[Unit] = {
    database.run((for {
      _ <- database.largeObjectManager.open(loid, LargeObject.Mode.Read)
    } yield ()).transactionally)
  }

  /** Returns an Enumerator over the given large object. */
  private def enumerateLargeObject(loid: Long): Enumerator[Array[Byte]] = {
    // Reads [position,position+BufferSize) bytes.
    //
    // Returns Some(newPosition, bytes) if there are more bytes; otherwise
    // returns None
    def continue(position: Int): Future[Option[(Int, Array[Byte])]] = {
      val futureBytes: Future[Array[Byte]] = database.run((for {
        lo <- database.largeObjectManager.open(loid, LargeObject.Mode.Read)
        _ <- lo.seek(position)
        bytes <- lo.read(BufferSize)
      } yield bytes).transactionally) // Future[Array[Byte]]

      futureBytes.map { bytes =>
        if (bytes.length == 0) {
          None
        } else {
          Some(position + bytes.length, bytes)
        }
      }
    }

    Enumerator.unfoldM(0)(continue _)
  }

  override def get(location: String): Future[Enumerator[Array[Byte]]] = {
    val loid = locationToOid(location)
    testLargeObject(loid)
      .map(_ => enumerateLargeObject(loid))
  }

  override def delete(location: String): Future[Unit] = deleteMany(Seq(location))

  override def deleteMany(locations: Seq[String]): Future[Unit] = {
    // LO.delete() puts the connection in an inconsistent state if the loid is
    // invalid. But our contract states the loid may be invalid. So we can't
    // use LO.delete() ... or lo_unlink() even. We need some nifty SQL.

    val groups: List[Seq[Long]] = locations.map(locationToOid _).grouped(DeleteManyChunkSize).toList

    def deleteSome(loids: Seq[Long]): Future[Unit] = database.runUnit(sqlu"""
      DO $$$$
      DECLARE
        loids BIGINT[] := ARRAY[#${loids.mkString(",")}];
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
    """)

    def continue(remainingGroups: List[Seq[Long]]): Future[Unit] = remainingGroups match {
      case someOids :: moreGroups => for {
        _ <- deleteSome(someOids)
        _ <- continue(moreGroups)
      } yield ()
      case List() => Future.successful(())
    }

    continue(groups)
  }

  override def create(locationPrefix: String, inputStream: InputStream, nBytes: Long): Future[String] = {
    if (locationPrefix != "pglo") throw new IllegalArgumentException("locationPrefix must be pglo; got: " + locationPrefix);

    val buffer = new Array[Byte](BufferSize)

    def copyRemainingChunks(oid: Long, nBytesWritten: Long, nBytesRemaining: Long): Future[Unit] = {
      if (nBytesRemaining == 0) {
        Future.successful(())
      } else {
        val bytesRead = inputStream.read(buffer, 0, buffer.length)
        if (bytesRead == -1) {
          throw new IOException("Expected input stream to have more bytes than it actually does")
        } else if (bytesRead > nBytesRemaining) {
          throw new IOException("Expected input stream to have fewer bytes than it actually does")
        }

        val step: DBIO[Unit] = for {
          lo <- database.largeObjectManager.open(oid, LargeObject.Mode.Write)
          _ <- lo.seek(nBytesWritten)
          _ <- lo.write(buffer, 0, bytesRead)
        } yield ()

        for {
          _ <- database.run(step.transactionally)
          _ <- copyRemainingChunks(oid, nBytesWritten + bytesRead, nBytesRemaining - bytesRead)
        } yield ()
      }
    }

    for {
      oid <- database.run(database.largeObjectManager.create.transactionally)
      _ <- copyRemainingChunks(oid, 0, nBytes)
    } yield "pglo:" + oid
  }
}

object PgLoStrategy extends PgLoStrategy with DatabaseProvider
