package controllers.iteratees

import akka.stream.scaladsl.{Flow,Keep,Sink}
import akka.util.ByteString
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import controllers.backend.GroupedFileUploadBackend
import com.overviewdocs.models.GroupedFileUpload

trait GroupedFileUploadIteratee {
  protected val groupedFileUploadBackend: GroupedFileUploadBackend
  protected val bufferSize: Int

  def apply(upload: GroupedFileUpload, initialPosition: Long): Sink[ByteString,Future[Unit]] = {
    val buffer = Flow.fromGraph(new Chunker(bufferSize).named("Chunker"))
    val write = Sink.foldAsync(initialPosition)({ (position: Long, bytes: ByteString) =>
      groupedFileUploadBackend.writeBytes(upload.id, position, bytes.toArray)
        .map(_ => position + bytes.length)
    })

    buffer.toMat(write)((_, _) => Future.successful(()))
  }
}

object GroupedFileUploadIteratee extends GroupedFileUploadIteratee {
  override protected val groupedFileUploadBackend = GroupedFileUploadBackend
  override protected val bufferSize = 1024 * 1024
}
