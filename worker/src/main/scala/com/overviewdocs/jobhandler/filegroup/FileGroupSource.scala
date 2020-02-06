package com.overviewdocs.jobhandler.filegroup

import akka.actor.{ActorRef,Status}
import akka.stream.{CompletionStrategy,Materializer,OverflowStrategy}
import akka.stream.scaladsl.Source
import java.time.Instant
import scala.concurrent.{ExecutionContext,Future,Promise}

import com.overviewdocs.database.Database
import com.overviewdocs.models.FileGroup
import com.overviewdocs.models.tables.{File2s,FileGroups,GroupedFileUploads}
import com.overviewdocs.ingest.model.{FileGroupProgressState,ResumedFileGroupJob}

/** All the un-ingested FileGroups we have.
  *
  * The `.source` method produces the actual Source[FileGroup]. On
  * initialization, the FileGroups are loaded from the database. Afterwards,
  * calls to `.add(FileGroup)` will enqueue more elements.
  *
  * All FileGroups are queued in memory; if the buffer overflows, the Source
  * fails. A FileGroup consumes about as much memory as its metadata: 5kb tops
  * for reasonable FileGroups. So 10,000 FileGroups means max 50MB.
  */
class FileGroupSource(
  val database: Database,
  onProgress: FileGroupProgressState => Unit,
  bufferSize: Int = 10000
)(implicit ec: ExecutionContext, mat: Materializer) {
  @volatile private var sourceActorRef: ActorRef = _

  /** Source of FileGroups.
    *
    * This will start with all FileGroups in the database; it will then produce
    * all FileGroups sent to `.enqueue`.
    */
  val source: Source[ResumedFileGroupJob, akka.NotUsed] = {
    val matchSuccess: PartialFunction[Any, CompletionStrategy] = { case Status.Success(_) => CompletionStrategy.draining }
    val matchFailure: PartialFunction[Any, Throwable] = { case Status.Failure(ex) => ex }

    Source.actorRef[(FileGroup,() => Unit)](
      matchSuccess,
      matchFailure,
      bufferSize,
      OverflowStrategy.fail
    )
      .mapMaterializedValue(mat => { sourceActorRef = mat; akka.NotUsed })
      .mapAsync(1)((resumeFileGroupJob _).tupled)
  }

  /** Actor which accepts FileGroups for emitting.
    *
    * Usage: `fileGroupSource.enqueue ! fileGroup` => will lead to `.source`
    * producing the given fileGroup.
    */
  lazy val enqueue: ActorRef = sourceActorRef

  private lazy val fileGroupIngestStatsCompiled = {
    import database.api._
    Compiled { fileGroupId: Rep[Long] =>
      for {
        gfu <- GroupedFileUploads if gfu.fileGroupId === fileGroupId
        file2 <- File2s if file2.id === gfu.file2Id if file2.ingestedAt.nonEmpty
      } yield file2.blobNBytes.get
    }
  }

  private def resumeFileGroupJob(
    fileGroup: FileGroup,
    onComplete: () => Unit
  ): Future[ResumedFileGroupJob] = {
    for {
      nBytesVec: Vector[Long] <- database.seq(fileGroupIngestStatsCompiled(fileGroup.id))
    } yield ResumedFileGroupJob(
      fileGroup,
      new FileGroupProgressState(
        fileGroup,
        nBytesVec.size,
        nBytesVec.fold(0L)(_ + _),
        Instant.now,
        onProgress,
        Promise[akka.Done]()
      ),
      onComplete
    )
  }
}
