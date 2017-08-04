package com.overviewdocs.messages

import scala.collection.immutable

import com.overviewdocs.query.Query

/** Tasks that read information from a document set.
  *
  * These commands are sent from the web server to the worker, and the worker
  * will send a response back. All messages are:
  *
  * * Queries: There are return values.
  * * Ephemeral: If the message is lost, it's lost.
  */
object DocumentSetReadCommands {
  sealed trait ReadCommand { val documentSetId: Long }

  /** Find a list of _all_ documents matching the search terms.
    *
    * This can get quite large. TODO turn the return value into a bitset?
    */
  case class Search(documentSetId: Long, query: Query) extends ReadCommand

  /** Find positions of every phrase in the document matching the query.
    */
  case class Highlight(documentSetId: Long, documentId: Long, query: Query) extends ReadCommand

  /** Find a few phrases from each document matching the query.
    */
  case class Highlights(documentSetId: Long, documentIds: immutable.Seq[Long], query: Query) extends ReadCommand
}
