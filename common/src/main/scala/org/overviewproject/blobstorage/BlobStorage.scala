package org.overviewproject.blobstorage

import java.io.InputStream
import play.api.libs.iteratee.Enumerator
import scala.concurrent.Future

/** Stores blobs, which are like files without the file part.
  *
  * Blobs are stored in "buckets", enumerated in BlobBucket. You create a blob
  * using create(), which returns a Location.
  *
  * Blobs are retrieved and deleted using Locations. A Location can be parsed
  * from a String that looks like <tt>"s3:bucket:key"</tt> or
  * <tt>"pglo:123"</tt>.
  *
  * All operations are asynchronous. Failures leave the system in an undefined
  * state. For instance, if you try to create a blob and the operation fails,
  * there may be a dangling blob somewhere. This interface gives no means to
  * avoid this problem; strategies must minimize the risk.
  */
trait BlobStorage {
  protected val config: BlobStorageConfig
  protected val strategyFactory: StrategyFactory

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
    * The <tt>bucketName</tt> must be a key in Config's
    * <tt>config.storage.preferredPrefixes</tt>.
    *
    * @param bucket What bundle of blobs to write to
    * @param inputStream Content to write
    * @param nBytes Number of bytes in the input stream
    * @return A <tt>location</tt>
    */
  def create(bucket: BlobBucketId, inputStream: InputStream, nBytes: Long): Future[String]
}

object BlobStorage extends BlobStorage {
  override protected val config = BlobStorageConfig
  override protected val strategyFactory = StrategyFactory

  override def get(location: String) = {
    strategyFactory.forLocation(location).get(location)
  }

  override def delete(location: String) = {
    strategyFactory.forLocation(location).delete(location)
  }

  override def create(bucket: BlobBucketId, inputStream: InputStream, nBytes: Long) = {
    val prefix = config.getPreferredPrefix(bucket)
    strategyFactory.forLocation(prefix).create(prefix, inputStream, nBytes)
  }
}
