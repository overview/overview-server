package com.overviewdocs.ingest

import akka.util.ByteString
import akka.stream.scaladsl.Source
import play.api.libs.json.JsObject
import scala.collection.immutable
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.database.Database
import com.overviewdocs.models.{BlobStorageRef,File2}
import com.overviewdocs.ingest.models.{CreatedFile2,WrittenFile2,ProcessedFile2,IngestedRootFile2}

class File2Writer(database: Database, blobStorage: BlobStorage) {
  def createChild(
    parentFile2: WrittenFile2,
    indexInParent: Int,
    filename: String,
    contentType: String,
    metadata: File2.Metadata,
    pipelineOptions: File2.PipelineOptions
  )(implicit ec: ExecutionContext): Future[CreatedFile2] = ???

  def deleteFile2(
    file2: CreatedFile2
  )(implicit ec: ExecutionContext): Future[Unit] = ???

  def writeBlob(
    file2: CreatedFile2,
    blob: Source[ByteString, _]
  )(implicit ec: ExecutionContext): Future[CreatedFile2] = ???

  def writeBlobStorageRef(
    file2: CreatedFile2,
    blobStorageRef: BlobStorageRef
  )(implicit ec: ExecutionContext): Future[CreatedFile2] = ???

  def writeThumbnail(
    file2: CreatedFile2,
    contentType: String,
    data: Source[ByteString, _]
  )(implicit ec: ExecutionContext): Future[CreatedFile2] = ???

  def writeText(
    file2: CreatedFile2,
    textUtf8: Source[ByteString, _]
  )(implicit ec: ExecutionContext): Future[CreatedFile2] = ???

  def setWritten(
    file2: CreatedFile2
  )(implicit ec: ExecutionContext): Future[WrittenFile2] = ???

  def setProcessed(
    file2: WrittenFile2,
    nChildren: Int,
    processingError: Option[String]
  )(implicit ec: ExecutionContext): Future[ProcessedFile2] = ???

  def ingestBatch(
    file2s: immutable.Seq[ProcessedFile2]
  )(implicit ec: ExecutionContext): Future[immutable.Seq[IngestedRootFile2]] = ???
}
