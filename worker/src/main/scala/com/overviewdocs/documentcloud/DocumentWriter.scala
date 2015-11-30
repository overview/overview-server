package com.overviewdocs.documentcloud

import java.util.{Timer,TimerTask}
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future,Promise}

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.{Document,DocumentCloudImport,DocumentProcessingError}
import com.overviewdocs.models.tables.DocumentProcessingErrors
import com.overviewdocs.util.BulkDocumentWriter

/** Wraps a BulkDocumentWriter and updates progress.
  *
  * Multiple fetchers write to this sink. Call flushPeriodically() to flush
  * until stop(). Like this:
  *
  *     val concurrentFetches = Seq(Future(...), Future(...))
  *     val writer = new DocumentWriter(dcImport)
  *     writer.flushPeriodically() // kicks off a timer.
  *     Future.sequence(concurrentFetches).map(_ => writer.stop)
  *     writer.stop // tells timer to write progress one last time, die, then return
  *
  * (There's no back pressure. If DocumentCloud is too fast, run fewer
  * concurrent Fetchers.)
  */
class DocumentWriter(
  dcImport: DocumentCloudImport,
  updateProgress: Int => Future[Unit],
  bulkDocumentWriter: BulkDocumentWriter = BulkDocumentWriter.forDatabaseAndSearchIndex,
  delayInMs: Int = 500
) extends HasDatabase {
  /** Array of (documentCloudId,message) */
  private val errors = mutable.Buffer[(String,String)]()

  /** Number of documents/errors/skips we've added to this writer. */
  private var nWritten = 0 // ignore DocumentCloudImport.nFetched: it isn't thread-safe, it's just for progressbar

  /** A caller told us to stop flushing. */
  private var stopped: Boolean = false

  private val timer: Timer = new Timer(s"DocumentWriter for DocumentCloudImport ${dcImport.id}", true)
  private val donePromise = Promise[Unit]()

  def addDocument(document: Document): Unit = synchronized {
    bulkDocumentWriter.add(document)
    nWritten += 1
  }

  def addError(dcId: String, error: String): Unit = synchronized {
    errors.+=((dcId, error))
    nWritten += 1
  }

  def skip(n: Int): Unit = synchronized {
    nWritten += n
  }

  /** Starts a Timer that calls flush() periodically until we call done(). */
  def flushPeriodically: Unit = {
    timer.schedule(flushTask, delayInMs)
  }

  /** Stops the Timer and calls flush(). */
  def stop: Future[Unit] = synchronized {
    if (!stopped) {
      stopped = true
      timer.schedule(flushTask, 0)
    }
    donePromise.future
  }

  /** Returns a "batch" of things to add to the database. Clears buffers. */
  private def snapshot = synchronized {
    val theseErrors = errors.toArray
    errors.clear
    (bulkDocumentWriter.flush, theseErrors, nWritten)
  }

  private def flushTask: TimerTask = timerTask {
    val (bulkFlushFuture, theseErrors, thisNWritten) = snapshot

    for {
      _ <- bulkFlushFuture
      _ <- writeErrors(theseErrors)
      _ <- updateProgress(thisNWritten)
    } yield {
      if (stopped) {
        timer.cancel
        donePromise.success(())
      } else {
        timer.schedule(flushTask, delayInMs)
      }
    }
  }

  private lazy val errorInserter = DocumentProcessingErrors.map(_.createAttributes)

  private def writeErrors(errors: Array[(String,String)]): Future[Unit] = {
    if (errors.length == 0) {
      Future.successful(())
    } else {
      import database.api._

      database.runUnit(errorInserter.++=(errors.map(t => DocumentProcessingError.CreateAttributes(
        documentSetId=dcImport.documentSetId,
        textUrl=t._1,
        message=t._2,
        statusCode=None,
        headers=None
      ))))
    }
  }

  private def timerTask(f: => Unit): TimerTask = new TimerTask {
    override protected def run = f
  }
}
