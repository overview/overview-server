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

  protected def db[A](block: Session => A): Future[A] = Future {
    blocking {
        Database.withSlickSession { session =>
          block(session)
      }
    }
  }

  private val LocationRegex = """^pagebytea:(\d+)$""".r
  private case class Location(pageId: Long)

  private def stringToLocation(locationString: String): Location = locationString match {
    case LocationRegex(pageId) => Location(pageId.toLong)
  }

  override def get(locationString: String): Future[Enumerator[Array[Byte]]] = db { implicit session =>
    val location = stringToLocation(locationString)

    val q = Pages.filter(_.id === location.pageId)
    
    val data = for {
      p <- q.firstOption
      d <- p.data
    } yield {
      val dataStream = new ByteArrayInputStream(d)
      Enumerator.fromStream(dataStream)
    }

    data.get
  }

  override def delete(location: String): Future[Unit] = ???
  override def create(locationPrefix: String, inputStream: InputStream, nBytes: Long): Future[String] = ???
}

object PageByteAStrategy extends PageByteAStrategy {
}
