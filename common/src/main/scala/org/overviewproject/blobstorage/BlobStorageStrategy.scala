package org.overviewproject.blobstorage

import java.io.InputStream
import play.api.libs.iteratee.Enumerator
import scala.concurrent.Future

/** Stores blobs with a specific implementation.
  */
trait BlobStorageStrategy {
  /** Starts streaming a blob.
    *
    * The <tt>location</tt> should look like <tt>"s3:bucket:key"</tt> or
    * <tt>"pglo:123456"</tt>.
    *
    * This method checks <tt>location</tt> for syntax synchronously. The Future
    * it returns may fail if there is a network error or permissions problem.
    *
    * @param location Something like <tt>"s3:bucket:key"</tt> or <tt>"pglo:123"</tt>
    * @throws InvalidArgumentException if <tt>location</tt> is invalid
    */
  def get(location: String): Future[Enumerator[Byte]]

  /** Deletes a blob.
    *
    * The <tt>location</tt> should look like <tt>"s3:bucket:key"</tt> or
    * <tt>"pglo:123456"</tt>.
    *
    * This method checks <tt>location</tt> for syntax synchronously. The Future
    * it returns may fail if there is a network error or permissions problem.
    *
    * When this method ends successfully, you are guaranteed that the file
    * stopped existing on the server.
    *
    * This method will <em>succeed</em> if you attempt to delete a blob that
    * does not exist.
    *
    * @param location Something like <tt>"s3:bucket:key"</tt> or <tt>"pglo:123"</tt>
    * @throws InvalidArgumentException if <tt>location</tt> is invalid
    */
  def delete(location: String): Future[Unit]

  /** Writes a file and returns its identifier.
    *
    * @param locationPrefix Something like <tt>"s3:bucket"</tt> or <tt>"pglo"</tt>
    * @param inputStream Content to write
    * @param nBytes Number of bytes in the input stream
    * @return A location string for use in get() and delete()
    */
  def create(locationPrefix: String, inputStream: InputStream, nBytes: Long): Future[String]
}
