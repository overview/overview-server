package org.overviewproject.blobstorage

sealed trait BlobBucketId {
  val id: String
}

case object PageDataBlobBucketId extends BlobBucketId {
  override val id = "pageData"
}
