package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.ExecutionContext.Implicits.global
import org.overviewproject.blobstorage.BlobStorage
import java.io.InputStream
import scala.concurrent.Future
import java.io.BufferedInputStream
import org.overviewproject.blobstorage.BlobBucketId
import java.security.MessageDigest
import java.security.DigestInputStream


trait LargeObjectMover {

  protected val blobStorage: BlobStorage
  
  protected def largeObjectInputStream(oid: Long): InputStream
  
  protected def moveLargeObjectToBlobStorage(oid: Long, size: Long, bucket: BlobBucketId): Future[(String, Array[Byte])] = {
    val loStream = largeObjectInputStream(oid)
    val digest = MessageDigest.getInstance("SHA-1")
    val digestStream = new DigestInputStream(loStream, digest)
    
    for {
      location <- blobStorage.create(bucket, digestStream, size)
    } yield (location, digest.digest)
  } 
}