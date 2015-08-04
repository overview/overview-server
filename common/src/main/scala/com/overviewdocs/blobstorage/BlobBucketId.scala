package com.overviewdocs.blobstorage

sealed trait BlobBucketId {
  val id: String
}

object BlobBucketId {
  case object PageData extends BlobBucketId {
    override val id = "pageData"
  }

  case object FileContents extends BlobBucketId {
    override val id = "fileContents"
  }

  case object FileView extends BlobBucketId {
    override val id = "fileView"
  }
}
