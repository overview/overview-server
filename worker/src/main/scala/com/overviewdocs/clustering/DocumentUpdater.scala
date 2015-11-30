package com.overviewdocs.clustering

import scala.collection.mutable
import slick.dbio.{DBIOAction,Effect,NoStream,SynchronousDatabaseAction}
import slick.jdbc.JdbcBackend
import slick.util.DumpInfo

import com.overviewdocs.database.HasBlockingDatabase

/** Updates document.description, batched.
  *
  * Not thread-safe.
  */
class DocumentUpdater extends HasBlockingDatabase {
  private val BatchSize = 1000 // Max 500bytes/doc, but don't thrash Postgres
  private val batch = mutable.ArrayBuffer[(Long,String)]()

  /** Updates the document description -- batched. */
  def blockingUpdateKeywordsAndFlushIfNeeded(documentId: Long, keywords: Seq[String]): Unit = {
    batch.+=((documentId, keywords.mkString(" ")))
    if (batch.length >= BatchSize) {
      blockingFlush
    }
  }

  /** Sends all pending SQL requests. */
  def blockingFlush: Unit = {
    blockingDatabase.run(new FlushAction(batch))

    batch.clear
  }

  private class FlushAction(batch: Seq[(Long,String)])
  extends SynchronousDatabaseAction[Unit, NoStream, JdbcBackend, Effect.Write]
  {
    override def getDumpInfo = DumpInfo("DocumentUpdater.FlushAction")

    override def run(context: JdbcBackend#Context): Unit = {
      val statement = context.connection.prepareStatement("UPDATE document SET description = ? WHERE id = ?")

      try {
        batch.foreach { t =>
          statement.setString(1, t._2)
          statement.setLong(2, t._1)
          statement.addBatch
        }
        statement.executeBatch
      } finally {
        statement.close
      }
    }
  }
}
