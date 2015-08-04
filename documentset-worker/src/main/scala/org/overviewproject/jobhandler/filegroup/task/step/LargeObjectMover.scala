package com.overviewdocs.jobhandler.filegroup.task.step

import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.overviewdocs.blobstorage.BlobBucketId
import com.overviewdocs.blobstorage.BlobStorage

/**
 * Helper methods for classes that need to transfer large object data from
 * the database to [[BlobStorage]].
 */
trait LargeObjectMover {

  protected val blobStorage: BlobStorage

  /** Override to provide input large object */
  protected def largeObjectInputStream(oid: Long): InputStream

  /**
   * Moves the data to [[BlobStorage]].
   * @returns the location and sha1 of the data.
   */
  protected def moveLargeObjectToBlobStorage(oid: Long, size: Long, bucket: BlobBucketId)
  (implicit executor: ExecutionContext): Future[(String, Array[Byte])] = {

    for {
      (digestStream, digest) <- sha1Digest(oid)
      location <- blobStorage.create(bucket, digestStream, size)
    } yield (location, digest.digest)
  }

  private def sha1Digest(oid: Long): Future[(InputStream, MessageDigest)] = AsFuture {
    val loStream = largeObjectInputStream(oid)
    val digest = MessageDigest.getInstance("SHA-1")
    val digestStream = new DigestInputStream(loStream, digest)

    (digestStream, digest)
  }
}