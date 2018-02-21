package com.overviewdocs.models

import java.time.Instant
import play.api.libs.json.JsObject

/** A file derived (or copied) from a user upload.
  *
  * In Overview, each Document is the leaf of a tree of File2 objects. The root
  * is the file the user uploaded.
  *
  * A _leaf_ File2 (which holds Document data) is never a _root_ File2. Such a
  * case would mean Overview never examined the file contents.
  */
case class File2(
  /** Unique ID. */
  id: Long,

  /** File2 that the user uploaded to generate this File2. `None` if this is
    * the root.
    *
    * (Why not rootFile2Id == id when this is the root? Because that makes it
    * hard for SQL to enforce constraints and hard for Slick use the SEQUENCE.)
    */
  rootFile2Id: Option[Long],

  /** File2 that was used to generate this File2: None if this is the root. */
  parentFile2Id: Option[Long],

  /** Number of File2s derived from the parent File2 ahead of this one.
    *
    * This is 0 for a root File2, and it's 0 when the parent File2 generates
    * just one File2 child.
    */
  indexInParent: Int,

  /** Filename, or pseudo-filename: for instance, "archive.zip/Foo/bar.txt". */
  filename: String,

  /** Media type, as would in an HTTP GET response, specified by:
    *
    * * For _root_ File2: the user, during upload (default application/octet-stream)
    * * For interim File2: the parent processing step
    * * For _leaf_ File2: the parent processing step
    */
  contentType: String,

  /** ISO 639-1 language identifier, supplied by the user. */
  languageCode: String,

  /** Metadata supplied by user, augmented by pipeline. */
  metadataJson: JsObject,

  /** Options supplied by user, augmented by pipeline. */
  pipelineOptions: JsObject,

  /** Reference to "main" file data.
    *
    * * For the _root_ File2, this is the uploaded data.
    * * For interim File2s, this is the interim data (e.g., "archive.pst/Foo/x.doc").
    * * For the _leaf_ File2, this is PDF data.
    */
  blob: Option[BlobStorageRef],

  /** Checksum for the file data, used to detect duplicates. */
  blobSha1: Array[Byte],

  /** Thumbnail data.
    *
    * * For a _tail_ File2, this is always None.
    * * For an interim File2, this is optional.
    * * For a _head_ File2, this points to image data.
    */
  thumbnailBlob: Option[BlobStorageRef],

  /** Text data.
    *
    * * For a _tail_ File2, this is always None.
    * * For an interim File2, this is optional.
    * * For a _head_ File2, this is equal to document.text.
    */
  text: Option[String],

  /** When the creation of this File2 began.
    *
    * Building a File2 means copying data from a file converter to BlobStorage.
    * We need a database handle before the copy is complete, so we can resume or
    * delete from BlobStorage. It's possible for a File2 to be "created" and
    * have no data.
    */
  createdAt: Instant,

  /** When this File2's BlobStorage and text data became immutable.
    *
    * Only after a File2 becomes immutable do we begin creating documents from
    * it.
    */
  writtenAt: Option[Instant],

  /** When processing of this File2 ended.
    *
    * This means:
    *
    * * If this is a leaf, a Document or DocumentProcessingError refers to it.
    * * If this is a parent, all File2 children that are not leaves are WRITTEN.
    * * If this is a parent, all File2 children that are leaves are PROCESSED.
    */
  processedAt: Option[Instant]
) {
  def toStatefulFile2: StatefulFile2 = {
    if (processedAt.nonEmpty) {
      StatefulFile2.Processed(this)
    } else if (writtenAt.nonEmpty) {
      StatefulFile2.Written(this)
    } else {
      StatefulFile2.Created(this)
    }
  }
}

sealed trait StatefulFile2 {
  val file2: File2

  def id: Long = file2.id
  def rootFile2Id: Option[Long] = file2.rootFile2Id
  def parentFile2Id: Option[Long] = file2.parentFile2Id
  def indexInParent: Int = file2.indexInParent
  def filename: String = file2.filename
  def contentType: String = file2.contentType
  def languageCode: String = file2.languageCode
  def metadataJson: JsObject = file2.metadataJson
  def pipelineOptions: JsObject = file2.pipelineOptions
  def createdAt: Instant = file2.createdAt
}
object StatefulFile2 {
  case class Created(override val file2: File2) extends StatefulFile2

  case class Written(override val file2: File2) extends StatefulFile2 {
    def blob: BlobStorageRef = file2.blob.get
    def blobSha1: Array[Byte] = file2.blobSha1
    def thumbnailBlob: Option[BlobStorageRef] = file2.thumbnailBlob
    def text: Option[String] = file2.text
    def writtenAt: java.time.Instant = file2.writtenAt.get
  }

  case class Processed(override val file2: File2) extends StatefulFile2 {
    def blob: BlobStorageRef = file2.blob.get
    def blobSha1: Array[Byte] = file2.blobSha1
    def thumbnailBlob: Option[BlobStorageRef] = file2.thumbnailBlob
    def text: Option[String] = file2.text
    def writtenAt: java.time.Instant = file2.writtenAt.get
    def processedAt: java.time.Instant = file2.processedAt.get
  }
}
