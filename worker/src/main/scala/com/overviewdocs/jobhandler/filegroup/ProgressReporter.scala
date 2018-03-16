package com.overviewdocs.jobhandler.filegroup

import akka.actor.{Actor,Props}
import java.time.Instant
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.database.Database
import com.overviewdocs.ingest.model.FileGroupProgressState
import com.overviewdocs.models.FileGroup
import com.overviewdocs.models.tables.FileGroups

class ProgressReporter(
  val database: Database,

  /** Delays writing.
    *
    * This ensures we don't thrash when updating progress. Otherwise, a very
    * fast producer would flood the database with updates.
    */
  val writeDelay: FiniteDuration
) extends Actor {
  import ProgressReporter._
  import context.dispatcher

  private val pendingUpdates: mutable.Map[Long,FileGroupProgressState] = mutable.Map()
  private var isFlushScheduled: Boolean = false

  override def receive = {
    case update: FileGroupProgressState => {
      pendingUpdates(update.fileGroup.id) = update
      if (!isFlushScheduled) {
        isFlushScheduled = true
        scheduleFlush
      }
    }

    case Flush => {
      assert(isFlushScheduled)
      assert(pendingUpdates.nonEmpty)

      // Copy to a separate place in memory
      val updates = pendingUpdates.values.toArray.iterator
      pendingUpdates.clear

      flush(updates)
        .andThen { case _ =>
          assert(isFlushScheduled) // still

          if (pendingUpdates.isEmpty) {
            isFlushScheduled = false
          } else {
            scheduleFlush
            // and leave isFlushScheduled == true
          }
        }
    }
  }

  private lazy val writeProgressCompiled = {
    import com.overviewdocs.database.Slick.api._

    Compiled { fileGroupId: Rep[Long] =>
      FileGroups
        .filter(_.id === fileGroupId)
        .map(g => (g.nFilesProcessed, g.nBytesProcessed, g.estimatedCompletionTime))
    }
  }

  private def writeProgress(
    fileGroupId: Long,
    nFilesProcessed: Int,
    nBytesProcessed: Long,
    estimatedCompletionTime: Instant
  )(implicit ec: ExecutionContext): Future[Unit] = {
    import database.api._

    database.runUnit(writeProgressCompiled(fileGroupId).update((
      Some(nFilesProcessed),
      Some(nBytesProcessed),
      Some(estimatedCompletionTime)
    )))
  }

  private def flush(updates: Iterator[FileGroupProgressState]): Future[Unit] = {
    if (updates.hasNext) {
      val state = updates.next
      val report = state.getProgressReport
      writeProgress(state.fileGroup.id, report.nFilesProcessed, report.nBytesProcessed, report.estimatedCompletionTime)
        .flatMap { _ => flush(updates) }
    } else {
      Future.unit
    }
  }

  protected def scheduleFlush: Unit = context.system.scheduler.scheduleOnce(writeDelay, self, Flush)
}

object ProgressReporter {
  /** Internal (plus tests): write to the database right away. */
  private[filegroup] case object Flush

  private val DefaultWriteDelay = FiniteDuration(200, "ms")

  def props: Props = {
    Props(new ProgressReporter(Database(), DefaultWriteDelay))
  }
}
