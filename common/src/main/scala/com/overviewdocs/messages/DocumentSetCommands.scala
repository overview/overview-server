package com.overviewdocs.messages

/** Background tasks that must be serialized on a document set.
  *
  * These commands are sent from the web server to the worker. All messages
  * are:
  *
  * * Write-only: There are no return values.
  * * Backed up: The web server has stored the data elsewhere (in the
  *   database), so that if the worker is dead it will infer the message the
  *   next time it starts up.
  * * Serialized: on a given document set, only one message will be processed
  *   at a time. Messages are processed in FIFO order.
  */
object DocumentSetCommands {
  sealed trait Command {
    val documentSetId: Long
  }
  case class DeleteDocumentSet(documentSetId: Long) extends Command
  case class DeleteDocumentSetJob(documentSetId: Long, jobId: Long) extends Command
}
