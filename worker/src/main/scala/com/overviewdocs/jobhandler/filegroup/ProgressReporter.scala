package com.overviewdocs.jobhandler.filegroup

import akka.actor.{Actor,Props}
import java.time.Instant
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.models.FileGroup

class ProgressReporter(
  val addDocumentsImpl: AddDocumentsImpl,

  /** Delays writing.
    *
    * This ensures we don't thrash when updating progress. Otherwise, a very
    * fast producer would flood the database with updates.
    */
  val writeDelay: FiniteDuration
) extends Actor {
  import ProgressReporter._
  import context.dispatcher

  private val pendingUpdates: mutable.Map[Long,Update] = mutable.Map()
  private var isFlushScheduled: Boolean = false

  override def receive = {
    case update: Update => {
      pendingUpdates(update.fileGroupId) = update
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

  private def flush(updates: Iterator[Update]): Future[Unit] = {
    if (updates.hasNext) {
      val u = updates.next
      addDocumentsImpl.writeProgress(u.fileGroupId, u.nFilesProcessed, u.nBytesProcessed, u.estimatedCompletionTime)
        .flatMap { _ => flush(updates) }
    } else {
      Future.unit
    }
  }

  protected def scheduleFlush: Unit = context.system.scheduler.scheduleOnce(writeDelay, self, Flush)
}

object ProgressReporter {
  /** Schedule a progress update. */
  case class Update(fileGroupId: Long, nFilesProcessed: Int, nBytesProcessed: Long, estimatedCompletionTime: Instant)

  /** Internal (plus tests): write to the database right away. */
  private[filegroup] case object Flush

  private val DefaultWriteDelay = FiniteDuration(200, "ms")

  def props(addDocumentsImpl: AddDocumentsImpl): Props = {
    Props(new ProgressReporter(addDocumentsImpl, DefaultWriteDelay))
  }
}
