package org.overviewproject.blobstorage

import java.io.{File,InputStream}
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
  def get(location: String): Future[Enumerator[Array[Byte]]] = {
    strategyFactory.forLocation(location).get(location)
  }

  /** Streams the blob into a file, runs the callback, and deletes the file.
    *
    * Remember, when writing async code, that the File will be deleted as soon
    * as the callback completes. On UNIX that's fine: anything that has already
    * opened the file will keep a handle on it.
    */
  def getAsTempFile[A](location: String)(callback: File => A): Future[A] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    Future(callback(new File(".")))
  }

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
  def delete(location: String): Future[Unit] = {
    strategyFactory.forLocation(location).delete(location)
  }

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
    * method fails, some files may be deleted but not others; you may call this
    * method with the same argument again to retry.
    *
    * This method is spread across multiple tasks, so it may fail in multiple
    * ways. When that happens, the return value will only resolve to the first
    * failure; the rest will be hidden.
    *
    * @param locations Seq of Strings like <tt>"s3:bucket:key"</tt> and <tt>"pglo:123"</tt>
    * @throws InvalidArgumentException if a <tt>location</tt> is invalid
    */
  def deleteMany(locations: Seq[String]): Future[Unit] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    def itemToFuture(item: Tuple2[BlobStorageStrategy,Seq[String]]): Future[Unit] = item._1.deleteMany(item._2)

    val seqs: Seq[(BlobStorageStrategy,Seq[String])] = locations.groupBy(strategyFactory.forLocation(_)).toSeq
    Future.traverse(seqs)(itemToFuture).map((_) => (()))
  }

  /** Writes a file and returns its identifier.
    *
    * The <tt>bucketName</tt> must be a key in Config's
    * <tt>config.storage.preferredPrefixes</tt>.
    *
    * If you supply the wrong <tt>nBytes</tt>, you'll get undefined behavior.
    *
    * @param bucket What bundle of blobs to write to
    * @param inputStream Content to write
    * @param nBytes Number of bytes in the input stream
    * @return A <tt>location</tt>
    */
  def create(bucket: BlobBucketId, inputStream: InputStream, nBytes: Long): Future[String] = {
    val prefix = config.getPreferredPrefix(bucket)
    strategyFactory.forLocation(prefix).create(prefix, inputStream, nBytes)
  }
}

object BlobStorage extends BlobStorage {
  override protected val config = BlobStorageConfig
  override protected val strategyFactory = StrategyFactory
}
