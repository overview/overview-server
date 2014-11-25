package org.overviewproject.blobstorage

import java.io.{File,InputStream}
import play.api.libs.iteratee.Enumerator
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait FileStrategy extends BlobStorageStrategy {
  protected val config: BlobStorageConfig

  lazy val baseDirectory: File = new File(config.getFileBaseDirectory)

  private val LocationRegex = """^file:([-\w]+):([-\w]+)$""".r
  private case class Location(bucket: String, key: String)
  private def stringToLocation(string: String): Location = string match {
    case LocationRegex(bucket, key) => Location(bucket, key)
    case _ => throw new IllegalArgumentException(s"Invalid location string: '${string}'")
  }

  override def get(locationString: String): Future[Enumerator[Array[Byte]]] = {
    val location = stringToLocation(locationString)
    Future {
      val file = new File(new File(baseDirectory, location.bucket), location.key)
      Enumerator.fromFile(file)
    }
  }

  override def delete(location: String): Future[Unit] = ???
  override def create(locationPrefix: String, inputStream: InputStream, nBytes: Long): Future[String] = ???
}

object FileStrategy extends FileStrategy {
  override protected val config = BlobStorageConfig
}
