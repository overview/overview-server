package org.overviewproject.blobstorage

sealed trait Location

case class S3Location(bucket: String, key: String) extends Location
case class PgLoLocation(loid: Int) extends Location
case class PageByteALocation(pageId: Long) extends Location
case class FileLocation(bucket: String, key: String) extends Location
