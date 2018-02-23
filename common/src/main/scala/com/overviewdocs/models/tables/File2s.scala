package com.overviewdocs.models.tables

import java.time.Instant
import play.api.libs.json.{JsObject,Json}

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.{BlobStorageRef,File2}

class File2sImpl(tag: Tag) extends Table[File2](tag, "file2") {
  val jsonByteaColumnType = MappedColumnType.base[JsObject, Array[Byte]](
    Json.toBytes,
    Json.parse(_).as[JsObject]
  )

  def id = column[Long]("id", O.AutoInc, O.PrimaryKey)
  def rootFile2Id = column[Option[Long]]("root_file2_id")
  def parentFile2Id = column[Option[Long]]("parent_file2_id")
  def indexInParent = column[Int]("index_in_parent")
  def filename = column[String]("filename")
  def contentType = column[String]("content_type")
  def languageCode = column[String]("language_code")
  def metadataJson = column[JsObject]("metadata_json_utf8")(jsonByteaColumnType)
  def pipelineOptions = column[JsObject]("pipeline_options_json_utf8")(jsonByteaColumnType)
  def blobLocation = column[Option[String]]("blob_location")
  def blobNBytes = column[Option[Int]]("blob_n_bytes")
  def blobSha1 = column[Array[Byte]]("blob_sha1")
  def thumbnailBlobLocation = column[Option[String]]("thumbnail_blob_location")
  def thumbnailBlobNBytes = column[Option[Int]]("thumbnail_blob_n_bytes")
  def text = column[Option[String]]("text")
  def createdAt = column[Instant]("created_at")
  def writtenAt = column[Option[Instant]]("written_at")
  def processedAt = column[Option[Instant]]("processed_at")
  def nChildren = column[Option[Int]]("n_children")
  def processingError = column[Option[String]]("processing_error")
  def ingestedAt = column[Option[Instant]]("ingested_at")

  def * = (
    id,
    rootFile2Id,
    parentFile2Id,
    indexInParent,
    filename,
    contentType,
    languageCode,
    metadataJson,
    pipelineOptions,
    blobLocation,
    blobNBytes,
    blobSha1,
    thumbnailBlobLocation,
    thumbnailBlobNBytes,
    text,
    createdAt,
    writtenAt,
    processedAt,
    nChildren,
    processingError,
    ingestedAt
  ) <> ((File2s.build _).tupled, File2s.unbuild)
}

object File2s extends TableQuery(new File2sImpl(_)) {
  def build(
    id: Long,
    rootFile2Id: Option[Long],
    parentFile2Id: Option[Long],
    indexInParent: Int,
    filename: String,
    contentType: String,
    languageCode: String,
    metadataJson: JsObject,
    pipelineOptions: JsObject,
    blobLocation: Option[String],
    blobNBytes: Option[Int],
    blobSha1: Array[Byte],
    thumbnailBlobLocation: Option[String],
    thumbnailBlobNBytes: Option[Int],
    text: Option[String],
    createdAt: Instant,
    writtenAt: Option[Instant],
    processedAt: Option[Instant],
    nChildren: Option[Int],
    processingError: Option[String],
    ingestedAt: Option[Instant]
  ) = File2(
    id,
    rootFile2Id,
    parentFile2Id,
    indexInParent,
    filename,
    contentType,
    languageCode,
    metadataJson,
    pipelineOptions,
    (blobLocation, blobNBytes) match {
      case (Some(location), Some(nBytes)) => Some(BlobStorageRef(location, nBytes))
      case _ => None
    },
    blobSha1,
    (thumbnailBlobLocation, thumbnailBlobNBytes) match {
      case (Some(location), Some(nBytes)) => Some(BlobStorageRef(location, nBytes))
      case _ => None
    },
    text,
    createdAt,
    writtenAt,
    processedAt,
    nChildren,
    processingError,
    ingestedAt
  )

  def unbuild(f: File2) = Some((
    f.id,
    f.rootFile2Id,
    f.parentFile2Id,
    f.indexInParent,
    f.filename,
    f.contentType,
    f.languageCode,
    f.metadataJson,
    f.pipelineOptions,
    f.blob.map(_.location),
    f.blob.map(_.nBytes),
    f.blobSha1,
    f.thumbnailBlob.map(_.location),
    f.thumbnailBlob.map(_.nBytes),
    f.text,
    f.createdAt,
    f.writtenAt,
    f.processedAt,
    f.nChildren,
    f.processingError,
    f.ingestedAt
  ))
}
