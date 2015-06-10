package org.overviewproject.blobstorage

import java.io.{ByteArrayInputStream,InputStream}
import play.api.libs.iteratee.Enumerator
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.overviewproject.database.HasDatabase
import org.overviewproject.models.tables.Pages

trait PageByteAStrategy extends BlobStorageStrategy with HasDatabase {
  import database.api._

  private val LocationRegex = """^pagebytea:(\d+)$""".r
  private case class Location(pageId: Long)

  private def stringToLocation(locationString: String): Location = locationString match {
    case LocationRegex(pageId) => Location(pageId.toLong)
    case _                     => throw new IllegalArgumentException(s"Invalid location string: '${locationString}'")
  }

  override def get(locationString: String): Future[Enumerator[Array[Byte]]] = {
    val location = stringToLocation(locationString)

    database.option(Pages.filter(_.id === location.pageId))
      .map { maybePage =>
        maybePage
          .get // Page or exception
          .data.get // Array[Byte] or exception
      }
      .map(bytes => Enumerator.fromStream(new ByteArrayInputStream(bytes)))
  }

  /** A noop since we never write data */
  override def delete(location: String): Future[Unit] = Future.successful(())

  /**
   *  @throws NotImplementedError always, because we don't want to store blobs in the page table
   */
  override def create(locationPrefix: String, inputStream: InputStream, nBytes: Long): Future[String] =
    throw new NotImplementedError("Blobs cannot be stored in byte arrays in the database")

}

object PageByteAStrategy extends PageByteAStrategy
