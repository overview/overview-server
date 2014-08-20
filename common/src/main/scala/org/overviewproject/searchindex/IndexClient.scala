package org.overviewproject.searchindex

import scala.concurrent.Future

import org.overviewproject.tree.orm.{Document,DocumentSet} // FIXME should be models

/** Interacts with a search index.
  *
  * A search index is a program that stores all documents. The documents are
  * grouped by document set ID.
  *
  * Indexing is asynchronous and presumably none too quick. That's why all
  * indexings are bulk requests.
  *
  * Searching is very fast. Read about the syntax at
  * http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html
  *
  * The _database_ is authoritative, not the search index. So the search index
  * returns IDs which can then be used to query the database.
  */
trait IndexClient {
  /** Adds a DocumentSet.
    *
    * This is for sharding and routing: after calling this method, Documents
    * within this DocumentSet will be routed to a given shard. So don't add
    * documents until you've done this.
    *
    * If the document set is already added, this is a no-op.
    */
  def addDocumentSet(id: Long): Future[Unit]

  /** Removes a DocumentSet.
    *
    * Removes all documents with the given DocumentSet ID from the search
    * index, then removes the DocumentSet metadata.
    *
    * If the document set doesn't exist in the search index, this is a no-op.
    */
  def removeDocumentSet(id: Long): Future[Unit]

  /** Adds Documents.
    *
    * Be sure to call addDocumentSet() for all relevant document sets before
    * you call this.
    *
    * Any documents with the same ID in the search index will be overwritten.
    *
    * After this method succeeds, the documents are guaranteed to be
    * _eventually_ searchable. To make them searchable right away, call
    * refresh().
    */
  def addDocuments(documents: Iterable[Document]): Future[Unit]

  /** Returns IDs for matching documents.
    *
    * The query language is here:
    * http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html
    */
  def searchForIds(documentSetId: Long, q: String): Future[Seq[Long]]

  /** Guarantees all past added documents are searchable. */
  def refresh(): Future[Unit]
}
