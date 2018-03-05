package com.overviewdocs.jobhandler.filegroup

import akka.actor.ActorRef
import akka.stream.{Materializer,OverflowStrategy}
import akka.stream.scaladsl.Source
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.database.Database
import com.overviewdocs.models.FileGroup
import com.overviewdocs.models.tables.FileGroups

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
  bufferSize: Int = 10000
)(implicit ec: ExecutionContext, mat: Materializer) {
  private var sourceActorRef: ActorRef = _

  /** Source of FileGroups.
    *
    * This will start with all FileGroups in the database; it will then produce
    * all FileGroups sent to `.enqueue`.
    */
  val source: Source[FileGroup, akka.NotUsed] = {
    val resumeSourceFuture: Future[Source[FileGroup, akka.NotUsed]] = for {
      fileGroups <- toResume
    } yield Source(fileGroups)

    val actorSource = Source.actorRef(bufferSize, OverflowStrategy.fail)
      .mapMaterializedValue(mat => { sourceActorRef = mat; akka.NotUsed })

    Source.fromFutureSource(resumeSourceFuture)
      .mapMaterializedValue(_ => akka.NotUsed)
      .concat(actorSource)
  }

  /** Actor which accepts FileGroups for emitting.
    *
    * Usage: `fileGroupSource.enqueue ! fileGroup` => will lead to `.source`
    * producing the given fileGroup.
    */
  lazy val enqueue: ActorRef = sourceActorRef

  private def toResume: Future[Vector[FileGroup]] = {
    // No need to Compile this query: we only run it once, on startup
    import database.api._
    val query = FileGroups
      .filter(_.deleted === false)
      .filter(_.addToDocumentSetId.nonEmpty)
    database.seq(query)
  }
}
