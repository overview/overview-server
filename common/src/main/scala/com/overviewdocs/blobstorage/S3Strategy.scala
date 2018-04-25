package com.overviewdocs.blobstorage

import akka.stream.scaladsl.{Source,StreamConverters}
import akka.util.ByteString
import com.amazonaws.services.s3.{AmazonS3,AmazonS3Client,AmazonS3ClientBuilder}
import com.amazonaws.services.s3.transfer.{TransferManager,TransferManagerBuilder}
import com.amazonaws.services.s3.model.{AmazonS3Exception,DeleteObjectsRequest,GetObjectRequest,GeneratePresignedUrlRequest,MultiObjectDeleteException,ObjectMetadata}
import com.amazonaws.event.{ProgressEvent,ProgressEventType,ProgressListener}
import com.google.common.io.ByteStreams
import java.io.IOException
import java.nio.file.{Files,Path}
import java.util.UUID
import scala.collection.JavaConverters.iterableAsScalaIterable
import scala.concurrent.{Future,Promise,blocking}
import scala.util.Try

import com.overviewdocs.util.Logger

/** Uses S3 to send and receive files.
  *
  * Right now, this is implemented using the Java SDK. Straight HTTP requests
  * would probably save us some threads and temporary files.
  */
trait S3Strategy extends BlobStorageStrategy {
  protected val s3: AmazonS3
  protected val transferManager: TransferManager
  protected val logger: Logger

  private case class Location(bucket: String, key: String)
  private val LocationRegex = """^s3:([-.\w]+):([-\w]+)$""".r
  private def stringToLocation(s: String) = s match {
    case LocationRegex(bucket, key) => Location(bucket, key)
    case _ => throw new IllegalArgumentException("Invalid location string: '" + s + "'")
  }

  private case class LocationPrefix(bucket: String)
  private val LocationPrefixRegex = """^s3:([-.\w]+)$""".r
  private def stringToLocationPrefix(s: String) = s match {
    case LocationPrefixRegex(bucket) => LocationPrefix(bucket)
    case _ => throw new IllegalArgumentException("Invalid location prefix: '" + s + '"')
  }

  override def get(locationString: String): Source[ByteString, akka.NotUsed] = {
    val location = stringToLocation(locationString)

    import scala.concurrent.ExecutionContext.Implicits.global
    val futureSource = Future(blocking {
      val s3Object = s3.getObject(location.bucket, location.key)
      val inputStream = s3Object.getObjectContent()
      StreamConverters.fromInputStream(() => inputStream)
    })
    Source.fromFutureSource(futureSource)
      .mapMaterializedValue(_ => akka.NotUsed)
  }

  override def getBytes(locationString: String, maxNBytes: Int): Future[Array[Byte]] = {
    val location = stringToLocation(locationString)
    val request = new GetObjectRequest(location.bucket, location.key)
      .withRange(0, maxNBytes)

    Future(blocking {
      val s3Object = s3.getObject(location.bucket, location.key)
      val inputStream = s3Object.getObjectContent()
      val ret = ByteStreams.toByteArray(inputStream)
      inputStream.close()
      ret
    })(scala.concurrent.ExecutionContext.Implicits.global)
  }

  override def getUrl(locationString: String, mimeType: String): Future[String] = {
    val location = stringToLocation(locationString)
    val request = new GeneratePresignedUrlRequest(location.bucket, location.key)
    request.addRequestParameter("response-content-type", mimeType)
    import scala.concurrent.ExecutionContext.Implicits.global
    Future(blocking {
      s3.generatePresignedUrl(request).toString
    })
  }

  override def getUrlOpt(locationString: String, mimeType: String): Future[Option[String]] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    getUrl(locationString, mimeType).map(url => Some(url))
  }

  override def delete(location: String): Future[Unit] = deleteMany(Seq(location))

  override def deleteMany(locationStrings: Seq[String]): Future[Unit] = {
    /*
     * Send delete requests to S3, grouped by bucket, max 1000 per request.
     *
     * If any one fails, the others will still continue but we'll throw an
     * exception. That meets the API promise.
     *
     * Amazon's Java SDK does not provide a non-blocking way to do this. We
     * may wish to convert to its REST API to avoid that shortcoming.
     */
    import scala.concurrent.ExecutionContext.Implicits.global

    val MaxKeysPerRequest = 1000

    val locations = locationStrings.map(stringToLocation)
    val requests: Seq[DeleteObjectsRequest] = locations
      .groupBy(_.bucket)       // Map[bucketName:String,locations:Seq[Location]]
      .mapValues(_.map(_.key)) // Map[bucketName:String,keys:Seq[String]]
      .toSeq                   // Seq[(bucketName:String,keys:Seq[String])]
      .flatMap({ (bucket: String, keys: Seq[String]) =>
        keys.grouped(MaxKeysPerRequest).map((group) => (bucket, group))
        // Seq[(bucketName:String,keyGroup:Seq[String])], max 1000 keys per group
      }.tupled)
      .map({ (bucket: String, keyGroup: Seq[String]) =>
        new DeleteObjectsRequest(bucket).withKeys(keyGroup: _*)
      }.tupled)

    Future(blocking {
      var error: Option[Throwable] = None

      requests.foreach { request =>
        try {
          s3.deleteObjects(request)
        } catch {
          case multiEx: MultiObjectDeleteException => iterableAsScalaIterable(multiEx.getErrors).foreach { ex =>
            val message = s"Delete of ${ex.getKey} failed with code ${ex.getCode}: ${ex.getMessage}"
            ex.getCode match {
              case "InternalError" => logger.warn(message) // It's a server-side problem; leak the file
              case _ => throw new IOException(message) // We don't know; panic
            }
          }
          case ex: AmazonS3Exception if ex.getStatusCode == 404 =>
        }
      }

      error.foreach(ex => throw ex)
    })
  }

  override def create(locationPrefixString: String, dataPath: Path): Future[String] = {
    /*
     * We generate a random filename and upload to it.
     */
    import scala.concurrent.ExecutionContext.Implicits.global

    val promise = Promise[String]()

    val locationPrefix = stringToLocationPrefix(locationPrefixString)
    val key = UUID.randomUUID.toString

    val upload = transferManager.upload(locationPrefix.bucket, key, dataPath.toFile)

    def complete: Unit = {
      val result = Try(blocking {
        upload.waitForCompletion()
        s"s3:${locationPrefix.bucket}:${key}"
      })
      promise.complete(result)
    }

    upload.addProgressListener(new ProgressListener {
      override def progressChanged(event: ProgressEvent) = event.getEventType match {
        case ProgressEventType.TRANSFER_COMPLETED_EVENT => complete
        case ProgressEventType.TRANSFER_FAILED_EVENT => complete
        case _ => ()
      }
    })

    promise.future
  }
}

object S3Strategy extends S3Strategy {
  override lazy val s3 = AmazonS3ClientBuilder.standard
    // if a bucket name contains dots, AWS's presigned URLs will contain dots:
    // that's a bug, because "foo.bar.s3.amazonaws.com" has the wrong SSL
    // certificate. Instead, generate "path-style" URLs, which won't have
    // that problem.
    .withPathStyleAccessEnabled(true)
    .build

  override lazy val transferManager = TransferManagerBuilder.standard.withS3Client(s3).build

  override val logger = Logger.forClass(getClass)
}
