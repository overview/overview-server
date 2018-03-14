package com.overviewdocs.blobstorage

import akka.stream.scaladsl.Source
import akka.util.ByteString
import java.nio.file.Path
import scala.concurrent.Future

/** Stores blobs with a specific implementation.
  */
trait BlobStorageStrategy {
  /** Starts streaming a blob.
    *
    * The <tt>location</tt> should look like <tt>"s3:bucket:key"</tt> or
    * <tt>"pglo:123456"</tt>.
    *
    * This method checks <tt>location</tt> for syntax synchronously. The Source
    * it returns may fail if there is a network error or permissions problem.
    *
    * @param location Something like <tt>"s3:bucket:key"</tt> or <tt>"pglo:123"</tt>
    * @throws InvalidArgumentException if <tt>location</tt> is invalid
    */
  def get(location: String): Source[ByteString, akka.NotUsed]

  /** Gets a public URL the end-user can use to access the blob for a while.
    *
    * The <tt>location</tt> should look like <tt>"s3:bucket:key"</tt> or
    * <tt>"pglo:123456"</tt>.
    *
    * This method checks <tt>location</tt> for syntax synchronously. The URL
    * it returns may fail if there is a network error or permissions problem.
    *
    * Beware super-long URLs. One potential implementation is to stream the
    * entire blob into a data: URL. For large blobs, that can consume lots of
    * memory.
    *
    * @param location Something like <tt>"s3:bucket:key"</tt> or <tt>"pglo:123"</tt>
    * @throws InvalidArgumentException if <tt>location</tt> is invalid
    */
  def getUrl(location: String, mimeType: String): Future[String]

  /** Gets a public URL the end-user can use to access the blob for a while.
    *
    * The <tt>location</tt> should look like <tt>"s3:bucket:key"</tt> or
    * <tt>"pglo:123456"</tt>.
    *
    * This method checks <tt>location</tt> for syntax synchronously. The URL
    * it returns may fail if there is a network error or permissions problem.
    *
    * Some storage backends (like File) cannot return a URL: they will return
    * None.
    *
    * @param location Something like <tt>"s3:bucket:key"</tt> or <tt>"pglo:123"</tt>
    * @throws InvalidArgumentException if <tt>location</tt> is invalid
    */
  def getUrlOpt(location: String, mimeType: String): Future[Option[String]]

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
    * @param dataPath File on the filesystem containing the data
    * @return A location string for use in get() and delete()
    */
  def create(locationPrefix: String, dataPath: Path): Future[String]
}
