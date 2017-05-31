package com.overviewdocs.blobstorage

import akka.NotUsed
import akka.actor.ActorRefFactory
import akka.stream.{ActorMaterializer,IOResult}
import akka.stream.scaladsl.{FileIO,Source,Sink}
import akka.util.ByteString
import java.io.{File,InputStream}
import java.nio.file.{Files,Path}
import scala.concurrent.{Future,blocking}
import scala.util.{Failure,Success,Try}

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
    * This method checks <tt>location</tt> for syntax synchronously. The Source
    * it returns may fail if there is a network error or permissions problem.
    *
    * @param location Something like <tt>"s3:bucket:key"</tt> or <tt>"pglo:123"</tt>
    * @throws InvalidArgumentException if <tt>location</tt> is invalid
    */
  def get(location: String): Source[ByteString, NotUsed] = {
    strategyFactory.forLocation(location).get(location)
  }

  /** Streams the blob into a file, runs the callback, and deletes the file.
    *
    * The File will be deleted after the callback ends, whether it succeeds or
    * fails.
    *
    * If the caller is killed (or crashes) while executing the callback, the
    * temporary file will not be deleted.
    */
  def withBlobInTempFile[A](location: String)(callback: File => Future[A])(implicit system: ActorRefFactory): Future[A] = {
    import scala.concurrent.ExecutionContext.Implicits.global

    def callCallbackSafely(file: File): Future[A] = {
      Try[Future[A]](callback(file)) match {
        case Success(future) => future
        case Failure(error) => Future.failed(error)
      }
    }

    val source = get(location)
    for {
      file <- BlobStorage.createTempFile(location, source)
      result <- callCallbackSafely(file).andThen { case _ => /*blocking(file.delete)*/ }
    } yield result
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
    * The <tt>path</tt> must remain valid until the return value is resolved,
    * or you'll get undefined behavior.
    *
    * @param bucket What bundle of blobs to write to
    * @param path Which file to write
    * @return A <tt>location</tt>
    */
  def create(bucket: BlobBucketId, dataPath: Path): Future[String] = {
    val prefix = config.getPreferredPrefix(bucket)
    strategyFactory.forLocation(prefix).create(prefix, dataPath)
  }
}

object BlobStorage extends BlobStorage {
  override protected val config = BlobStorageConfig
  override protected val strategyFactory = StrategyFactory

  private def createTempFile(location: String, source: Source[ByteString, _])(implicit system: ActorRefFactory) : Future[File] = {
    import system.dispatcher

    implicit val materializer = ActorMaterializer.create(system)

    val path: Path = Files.createTempFile("blob-storage-" + location, null)
    source.runWith(FileIO.toPath(path))
      .map(_.status.map(_ => path.toFile).get)
  }
}
