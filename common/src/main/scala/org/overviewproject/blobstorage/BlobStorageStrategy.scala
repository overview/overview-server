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
  def get(location: String): Future[Enumerator[Array[Byte]]]

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

  /** Deletes lots of blobs, batched.
    *
    * Each <tt>location</tt> should look like <tt>"s3:bucket:key"</tt> or
    * <tt>"pglo:123456"</tt>.
    *
    * This method checks <tt>location</tt> for syntax synchronously. The Future
    * it returns may fail if there is a network error or permissions problem.
    *
    * This method will <em>succeed</em> if you attempt to delete a blob that
    * does not exist.
    *
    * When this method ends successfully, you are guaranteed that each file was
    * absent from the server at some point after you called the method. If the
    * method fails, some files may be deleted but not others. This is true even
    * if the exception is a synchronous <tt>IllegalArgumentException</tt>.
    *
    * @param locations Seq of Strings like <tt>"s3:bucket:key"</tt> and <tt>"pglo:123"</tt>
    * @throws InvalidArgumentException if a <tt>location</tt> is invalid
    */
  def deleteMany(locations: Seq[String]): Future[Unit] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    Future.traverse(locations)(delete).map((_) => (()))
  }

  /** Writes a file and returns its identifier.
    *
    * @param locationPrefix Something like <tt>"s3:bucket"</tt> or <tt>"pglo"</tt>
    * @param inputStream Content to write
    * @param nBytes Number of bytes in the input stream
    * @return A location string for use in get() and delete()
    */
  def create(locationPrefix: String, inputStream: InputStream, nBytes: Long): Future[String]
}
