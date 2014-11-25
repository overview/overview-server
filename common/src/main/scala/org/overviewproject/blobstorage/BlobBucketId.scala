package org.overviewproject.blobstorage

sealed trait BlobBucketId {
  val id: String
}

object BlobBucketId {
  case object PageData extends BlobBucketId {
    override val id = "pageData"
  }
}
