package controllers.iteratees

import play.api.libs.iteratee.{Enumeratee,Iteratee,Traversable}

import controllers.backend.GroupedFileUploadBackend
import org.overviewproject.models.GroupedFileUpload

trait GroupedFileUploadIteratee {
  protected val groupedFileUploadBackend: GroupedFileUploadBackend
  protected val bufferSize: Int

  def apply(upload: GroupedFileUpload, position: Long): Iteratee[Array[Byte],Unit] = {
    bufferedIteratee(upload, position)
  }

  /** buffers input and then passes it to a writeBytesIteratee. */
  private def bufferedIteratee(upload: GroupedFileUpload, position: Long): Iteratee[Array[Byte],Unit] = {
    val consumeOneChunk = Traversable.takeUpTo[Array[Byte]](bufferSize).transform(Iteratee.consume())
    val consumeChunks: Enumeratee[Array[Byte], Array[Byte]] = Enumeratee.grouped(consumeOneChunk)
    consumeChunks.transform(writeBytesIteratee(upload, position))
  }

  /** writes input to the specified GroupedFileUpload's Large Object */
  private def writeBytesIteratee(upload: GroupedFileUpload, initialPosition: Long): Iteratee[Array[Byte],Unit] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    Iteratee.foldM(initialPosition)({ (position: Long, bytes: Array[Byte]) =>
      groupedFileUploadBackend.writeBytes(upload.id, position, bytes)
        .map(_ => position + bytes.length)
    }).map(_ => ())
  }
}

object GroupedFileUploadIteratee extends GroupedFileUploadIteratee {
  override protected val groupedFileUploadBackend = GroupedFileUploadBackend
  override protected val bufferSize = 1024 * 1024
}
