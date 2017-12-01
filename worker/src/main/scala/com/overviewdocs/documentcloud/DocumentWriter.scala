package com.overviewdocs.documentcloud

import java.util.{Timer,TimerTask}
import scala.collection.mutable
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
  delayInMs: Int = 300
) extends HasDatabase {
  /** Array of (documentCloudId,message) */
  private val errors = mutable.Buffer[(String,String)]()

  /** Number of documents/errors/skips we've added to this writer. */
  private var nWritten = 0 // ignore DocumentCloudImport.nFetched: it isn't thread-safe, it's just for progressbar

  /** We have scheduled a flush. */
  private var isFlushScheduled: Boolean = false
  private var nActiveFlushes = 0

  /** A caller told us to stop flushing. */
  private var stopped: Boolean = false

  /*
   * The timer is a single thread, and it executes flushTask on its own. Nothing
   * else executes flushTask.
   */
  private val timer: Timer = new Timer(s"DocumentWriter for DocumentCloudImport ${dcImport.id}", true)
  private val donePromise = Promise[Unit]()

  private def ensureFlushIsScheduled: Unit = synchronized {
    if (!isFlushScheduled) {
      isFlushScheduled = true
      timer.schedule(flushTask, delayInMs)
    }
  }

  def addDocument(document: Document): Unit = synchronized {
    if (stopped) throw new RuntimeException("called addDocument() on dead DocumentWriter")
    bulkDocumentWriter.add(document)
    nWritten += 1
    ensureFlushIsScheduled
  }

  def addError(dcId: String, error: String): Unit = synchronized {
    if (stopped) throw new RuntimeException("called addError() on dead DocumentWriter")
    errors.+=((dcId, error))
    nWritten += 1
    ensureFlushIsScheduled
  }

  def skip(n: Int): Unit = synchronized {
    if (stopped) throw new RuntimeException("called skip() on dead DocumentWriter")
    nWritten += n
    ensureFlushIsScheduled // so we call updateProgress
  }

  /** Stops the Timer and calls flush(). */
  def stop: Future[Unit] = synchronized {
    if (!stopped) {
      stopped = true

      // it's tempting to set donePromise.success(()) here. But we don't know
      // whether the timer is flushing right now, so we can't.

      // Schedule a _faster_ flush. This guarantees that only one
      // thread calls startFlush, which makes life much simpler
      timer.schedule(flushTask, 0)
    }

    donePromise.future
  }

  /** Returns a "batch" of things to add to the database. Clears buffers. */
  private def snapshot: (Future[Unit], Array[(String,String)], Int) = synchronized {
    val theseErrors = errors.toArray
    errors.clear
    (bulkDocumentWriter.flush, theseErrors, nWritten)
  }

  private def doFlush(aSnapshot: (Future[Unit], Array[(String,String)], Int)): Future[Unit] = {
    val (bulkFlushFuture, theseErrors, thisNWritten) = aSnapshot

    import scala.concurrent.ExecutionContext.Implicits.global

    for {
      _ <- bulkFlushFuture
      _ <- writeErrors(theseErrors)
      _ <- updateProgress(thisNWritten)
    } yield {
    }
  }

  private def startFlush: Unit = {
    // This is called within the Timer thread -- so it's quasi-synchronous

    val aSnapshot = synchronized {
      if (stopped) {
        // When we set stopped=true, we may have added a second flush task to
        // the timer queue. timer.cancel() guarantees we'll never see it.
        timer.cancel
      }

      isFlushScheduled = false
      nActiveFlushes += 1 // Actually, these can race each other. We _really_
                          // need to replace this architecture with akka Streams.

      snapshot
    }

    import scala.concurrent.ExecutionContext.Implicits.global
    doFlush(aSnapshot)
      .onComplete(_ => synchronized {
        nActiveFlushes -= 1
        if (stopped && nActiveFlushes == 0) {
          donePromise.success(())
        }
      })
  }

  private def flushTask: TimerTask = timerTask { startFlush }

  private def writeErrors(errors: Array[(String,String)]): Future[Unit] = {
    if (errors.length == 0) {
      Future.unit
    } else {
      import database.api._

      val createAttributesList = errors.map(t => DocumentProcessingError.CreateAttributes(
        documentSetId=dcImport.documentSetId,
        textUrl=t._1,
        message=t._2,
        statusCode=None,
        headers=None
      ))

      database.runUnit(DocumentWriter.errorInserter.++=(createAttributesList))
    }
  }

  private def timerTask(f: => Unit): TimerTask = new TimerTask {
    override protected def run = f
  }
}

object DocumentWriter {
  private lazy val errorInserter = DocumentProcessingErrors.map(_.createAttributes)
}
