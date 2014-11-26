package org.overviewproject.blobstorage

import com.amazonaws.services.s3.{AmazonS3,AmazonS3Client}
import com.amazonaws.services.s3.transfer.TransferManager
import com.amazonaws.services.s3.model.{AmazonS3Exception,DeleteObjectsRequest,ObjectMetadata}
import com.amazonaws.event.{ProgressEvent,ProgressEventType,ProgressListener}
import java.io.InputStream
import java.nio.file.Files
import java.util.UUID
import play.api.libs.iteratee.Enumerator
import scala.concurrent.{Future,Promise,blocking}
import scala.util.Try

/** Uses S3 to send and receive files.
  *
  * Right now, this is implemented using the Java SDK. Straight HTTP requests
  * would probably save us some threads and temporary files.
  */
trait S3Strategy extends BlobStorageStrategy {
  protected val s3: AmazonS3
  protected val transferManager: TransferManager

  private case class Location(bucket: String, key: String)
  private val LocationRegex = """^s3:([-\w]+):([-\w]+)$""".r
  private def stringToLocation(s: String) = s match {
    case LocationRegex(bucket, key) => Location(bucket, key)
    case _ => throw new IllegalArgumentException("Invalid location string: '" + s + "'")
  }

  private case class LocationPrefix(bucket: String)
  private val LocationPrefixRegex = """^s3:([-\w]+)$""".r
  private def stringToLocationPrefix(s: String) = s match {
    case LocationPrefixRegex(bucket) => LocationPrefix(bucket)
    case _ => throw new IllegalArgumentException("Invalid location prefix: '" + s + '"')
  }

  override def get(locationString: String): Future[Enumerator[Array[Byte]]] = {
    /*
     * We'll download to a temporary file, then create an Enumerator from it.
     *
     * Why? Well ... AWS offers an interface to grab an InputStream, but that's
     * a blocking interface so it doesn't help much. And we can't listen to the
     * number of bytes read in a ProgressEvent: TransferManager downloads
     * chunks of a file in parallel, so we can't know how many bytes of the
     * file are _sequentially_ readable.
     *
     * There are a few spots this method blocks, because we think the
     * consequences are minor and the time savings are substantial:
     *
     * - We determine a download filename by creating it.
     * - We delete the download file using File.delete()
     * - We use Enumerator.fromFile() (this one isn't our bad, really....)
     *
     * We also assume the OS allows deleting an open file.
     */
    import scala.concurrent.ExecutionContext.Implicits.global
    val location = stringToLocation(locationString)

    // This is blocking, but /tmp ought to be very fast
    val filePath = Files.createTempFile("overview-s3-transfer-", ".tmp")
    val file = filePath.toFile

    val download = transferManager.download(location.bucket, location.key, file)

    val promise = Promise[Enumerator[Array[Byte]]]()

    download.addProgressListener(new ProgressListener {
      override def progressChanged(event: ProgressEvent) = event.getEventType match {
        case ProgressEventType.TRANSFER_COMPLETED_EVENT => {
          val ret = Enumerator.fromFile(file)
          file.delete
          promise.success(ret)
        }
        case ProgressEventType.TRANSFER_FAILED_EVENT => {
          file.delete
          promise.failure(blocking(download.waitForException()))
        }
        case _ => ()
      }
    })

    promise.future
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

    Future.traverse(requests) { (request: DeleteObjectsRequest) => Future[Unit] { blocking {
      try {
        s3.deleteObjects(request)
      } catch {
        case ex: AmazonS3Exception if ex.getStatusCode == 404 =>
      }
    }}}
      .map((_) => ()) // Seq[Unit] => Unit. side-feature: it looks like a wink
  }

  override def create(locationPrefixString: String, inputStream: InputStream, nBytes: Long): Future[String] = {
    /*
     * We generate a random filename and upload to it.
     */
    import scala.concurrent.ExecutionContext.Implicits.global

    val locationPrefix = stringToLocationPrefix(locationPrefixString)
    val key = UUID.randomUUID.toString

    val metadata = new ObjectMetadata()
    metadata.setContentLength(nBytes)

    val promise = Promise[String]()
    val upload = transferManager.upload(locationPrefix.bucket, key, inputStream, metadata)

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
  override lazy val s3 = {
    val awsCredentials = BlobStorageConfig.awsCredentials
    new AmazonS3Client(awsCredentials)
  }

  override lazy val transferManager = new TransferManager(s3)
}
