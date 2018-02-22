package com.overviewdocs.ingest

import akka.util.ByteString
import akka.stream.scaladsl.Source
import play.api.libs.json.JsObject
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.database.Database
import com.overviewdocs.models.File2

class File2Writer(database: Database, blobStorage: BlobStorage) {
  def createChild(
    parentFile2: File2,
    indexInParent: Int,
    filename: String,
    contentType: String,
    metadataJson: JsObject,
    pipelineOptions: JsObject
  )(implicit ec: ExecutionContext): Future[File2] = ???

  def writeData(
    file2: File2,
    data: Source[ByteString, _]
  )(implicit ec: ExecutionContext): Future[File2] = ???

  def writeThumbnail(
    file2: File2,
    contentType: String,
    data: Source[ByteString, _]
  )(implicit ec: ExecutionContext): Future[File2] = ???

  def writeText(
    file2: File2,
    textUtf8: Source[ByteString, _]
  )(implicit ec: ExecutionContext): Future[File2] = ???

  def setWritten(
    file2: File2
  )(implicit ec: ExecutionContext): Future[File2] = ???

  def setProcessed(
    file2: File2,
    maybeError: Option[String]
  )(implicit ec: ExecutionContext): Future[File2] = ???

  def setIngested(
    file2: File2
  )(implicit ec: ExecutionContext): Future[File2] = ???
}
