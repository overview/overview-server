package com.overviewdocs.models

/** All one needs to serve from BlobStorage with a Content-Length header.
  */
case class BlobStorageRef(
  location: String,
  nBytes: Long
)
