package org.overviewproject.blobstorage

import java.io.{ File, InputStream, IOException }
import play.api.libs.iteratee.Enumerator
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future,blocking}
import java.util.UUID
import java.nio.file.Files

trait FileStrategy extends BlobStorageStrategy {
  protected val config: BlobStorageConfig

  lazy val baseDirectory: File = new File(config.fileBaseDirectory)

  private val LocationRegex = """^file:([-\w]+):([-\w]+)$""".r
  private case class Location(bucket: String, key: String)
  private def stringToLocation(string: String): Location = string match {
    case LocationRegex(bucket, key) => Location(bucket, key)
    case _ => throw new IllegalArgumentException(s"Invalid location string: '${string}'")
  }

  private def keyFile(location: Location): File =
    new File(new File(baseDirectory, location.bucket), location.key)

  private def createNewLocationString(locationPrefix: String) = 
    s"$locationPrefix:${UUID.randomUUID}"
  
  override def get(locationString: String): Future[Enumerator[Array[Byte]]] = {
    val location = stringToLocation(locationString)
    Future {
      val file = keyFile(location)
      Enumerator.fromFile(file)
    }
  }

  override def delete(locationString: String): Future[Unit] = {
    val location = stringToLocation(locationString)
    val file = keyFile(location)
    Future { blocking {
      Files.deleteIfExists(file.toPath)
    } }
  }

  override def create(locationPrefix: String, inputStream: InputStream, nBytes: Long): Future[String] = {
    val locationString = createNewLocationString(locationPrefix)
    val location = stringToLocation(locationString)

    Future {
      val filePath = keyFile(location).toPath()
      Files.createDirectories(filePath.getParent)
      Files.copy(inputStream, filePath)

      locationString
    }
  }

}

object FileStrategy extends FileStrategy {
  override protected val config = BlobStorageConfig
}
