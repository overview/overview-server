package com.overviewdocs.models.tables

import java.time.Instant
import slick.lifted.{Rep=>LRep}

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.{BlobStorageRef,File2}

class File2sImpl(tag: Tag) extends Table[File2](tag, "file2") {
  def id = column[Long]("id", O.AutoInc, O.PrimaryKey)
  def rootFile2Id = column[Option[Long]]("root_file2_id")
  def parentFile2Id = column[Option[Long]]("parent_file2_id")
  def indexInParent = column[Int]("index_in_parent")
  def filename = column[String]("filename")
  def contentType = column[String]("content_type")
  def languageCode = column[String]("language_code")
  def metadata = column[File2.Metadata]("metadata_json_utf8")
  def wantOcr = column[Boolean]("want_ocr")
  def wantSplitByPage = column[Boolean]("want_split_by_page")
  def blobLocation = column[Option[String]]("blob_location")
  def blobNBytes = column[Option[Int]]("blob_n_bytes")
  def blobSha1 = column[Array[Byte]]("blob_sha1")
  def thumbnailBlobLocation = column[Option[String]]("thumbnail_blob_location")
  def thumbnailBlobNBytes = column[Option[Int]]("thumbnail_blob_n_bytes")
  def thumbnailContentType = column[Option[String]]("thumbnail_content_type")
  def text = column[Option[String]]("text")
  def createdAt = column[Instant]("created_at")
  def writtenAt = column[Option[Instant]]("written_at")
  def processedAt = column[Option[Instant]]("processed_at")
  def nChildren = column[Option[Int]]("n_children")
  def processingError = column[Option[String]]("processing_error")
  def ingestedAt = column[Option[Instant]]("ingested_at")

  private def optionTupleToBlobStorageRef(t: (Option[String],Option[Int])): Option[BlobStorageRef] = {
    t match {
      case (Some(location), Some(nBytes)) => Some(BlobStorageRef(location, nBytes))
      case _ => None
    }
  }

  private def blobStorageRefOptToOptionTuple(bsOpt: Option[BlobStorageRef]): Option[(Option[String],Option[Int])] = {
    Some((bsOpt.map(_.location), bsOpt.map(_.nBytes)))
  }

  def blob = (blobLocation, blobNBytes).<>(optionTupleToBlobStorageRef _, blobStorageRefOptToOptionTuple _)
  def thumbnailBlob = (thumbnailBlobLocation, thumbnailBlobNBytes).<>(optionTupleToBlobStorageRef _, blobStorageRefOptToOptionTuple _)

  def * = (
    id,
    rootFile2Id,
    parentFile2Id,
    indexInParent,
    filename,
    contentType,
    languageCode,
    metadata,
    wantOcr,
    wantSplitByPage,
    blob,
    blobSha1,
    thumbnailBlob,
    thumbnailContentType,
    text,
    createdAt,
    writtenAt,
    processedAt,
    nChildren,
    processingError,
    ingestedAt
  ).shaped <> ((File2.apply _).tupled, File2.unapply _)
}

object File2s extends TableQuery(new File2sImpl(_))
