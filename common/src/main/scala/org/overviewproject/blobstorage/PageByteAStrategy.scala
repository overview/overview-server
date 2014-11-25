package org.overviewproject.blobstorage

import java.io.InputStream
import play.api.libs.iteratee.Enumerator
import scala.concurrent.Future

trait PageByteAStrategy extends BlobStorageStrategy {
  override def get(location: String): Future[Enumerator[Byte]] = ???
  override def delete(location: String): Future[Unit] = ???
  override def create(locationPrefix: String, inputStream: InputStream, nBytes: Long): Future[String] = ???
}

object PageByteAStrategy extends PageByteAStrategy {
}
