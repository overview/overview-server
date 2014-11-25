package org.overviewproject.blobstorage

import java.io.{File,InputStream, IOException}
import play.api.libs.iteratee.Enumerator
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
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
  
  override def get(locationString: String): Future[Enumerator[Array[Byte]]] = {
    val location = stringToLocation(locationString)
    Future {
      val file = keyFile(location) 
      Enumerator.fromFile(file)
    }
  }

  override def delete(locationString: String): Future[Unit] = {
    val location = stringToLocation(locationString)
    Future {
      val file = keyFile(location)
      if (!file.delete) throw new IOException(s"Unable to delete file at location $locationString")
    }
  }
  
  override def create(locationPrefix: String, inputStream: InputStream, nBytes: Long): Future[String] = {
    Future {
      val key = UUID.randomUUID
      val locationString = s"$locationPrefix:$key"
      val location = stringToLocation(locationString)
      val file = keyFile(location)
      
      Files.copy(inputStream, file.toPath)
      
      locationString
    }
  }
  
}

object FileStrategy extends FileStrategy {
  override protected val config = BlobStorageConfig
}
