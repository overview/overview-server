package org.overviewproject.blobstorage

import java.io.InputStream
import play.api.libs.iteratee.Enumerator
import scala.concurrent.{ Future, blocking }
import scala.concurrent.ExecutionContext.Implicits.global
import org.overviewproject.database.Database
import org.overviewproject.models.tables.Pages
import org.overviewproject.database.Slick.simple._
import java.io.ByteArrayInputStream


trait PageByteAStrategy extends BlobStorageStrategy {

  protected def db[A](block: Session => A): Future[A]

  private val LocationRegex = """^pagebytea:(\d+)$""".r
  private case class Location(pageId: Long)

  private def stringToLocation(locationString: String): Location = locationString match {
    case LocationRegex(pageId) => Location(pageId.toLong)
    case _ => throw new IllegalArgumentException(s"Invalid location string: '${locationString}'")
  }

  override def get(locationString: String): Future[Enumerator[Array[Byte]]] = {
    val location = stringToLocation(locationString)

    db { session =>

      val q = Pages.filter(_.id === location.pageId)

      val page = q.first(session) // or exception
      val data = page.data.get // or exception
      val dataStream = new ByteArrayInputStream(data)

      Enumerator.fromStream(dataStream)
    }
  }

  /** A noop since we never write data */
  override def delete(location: String): Future[Unit] = Future.successful()
  
  /** 
   *  @throws NotImplementedError always, because we don't want to store blobs in the page table
   */
  override def create(locationPrefix: String, inputStream: InputStream, nBytes: Long): Future[String] = 
    throw new NotImplementedError("Blobs cannot be stored in byte arrays in the database")
  
}

object PageByteAStrategy extends PageByteAStrategy {
  override protected def db[A](block: Session => A): Future[A] = Future {
    blocking {
      Database.withSlickSession { session =>
        block(session)
      }
    }
  }
  
}
