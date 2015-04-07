package org.overviewproject.jobhandler.filegroup.task.step

import org.overviewproject.blobstorage.BlobStorage
import java.io.InputStream
import scala.concurrent.Future
import java.io.BufferedInputStream
import org.overviewproject.blobstorage.BlobBucketId


trait LargeObjectMover {
  private val BufferSize = 5 * 1024 * 1024

  protected val blobStorage: BlobStorage
  
  protected def largeObjectInputStream(oid: Long): InputStream
  
  protected def moveLargeObjectToBlobStorage(oid: Long, size: Long, bucket: BlobBucketId): Future[String] = {
    val loStream = largeObjectInputStream(oid)
    val stream = new BufferedInputStream(loStream, BufferSize)
    
    blobStorage.create(bucket, stream, size)
  }
}