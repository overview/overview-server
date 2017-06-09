package com.overviewdocs.searchindex

import scala.concurrent.Future

import com.overviewdocs.query.Query
import com.overviewdocs.models.Document

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
    * After this method succeeds, the documents are searchable right away. (It
    * writes to disk and calls fsync before completing.)
    */
  def addDocuments(documentSetId: Long, documents: Iterable[Document]): Future[Unit]

  /** Adds Documents, without necessarily writing to disk.
    *
    * Call refresh(documentSetId) to make sure the documents are stored and
    * searchable.
    */
  def addDocumentsWithoutFsync(documentSetId: Long, documents: Iterable[Document]): Future[Unit]

  /** Returns IDs for matching documents.
    *
    * @param documentSetId Document set ID
    * @param q Search string
    */
  def searchForIds(documentSetId: Long, q: Query): Future[SearchResult]

  /** Finds all highlights of a given query in a document.
    *
    * @param documentSetId Document set ID (says which alias to search under)
    * @param documentId Document ID
    * @param q Search string
    */
  def highlight(documentSetId: Long, documentId: Long, q: Query): Future[Seq[Utf16Highlight]]

  def highlights(documentSetId: Long, documentIds: Seq[Long], q: Query): Future[Map[Long, Seq[Utf16Snippet]]]

  /** Guarantees all past added documents are searchable. */
  def refresh(documentSetId: Long): Future[Unit]

  /** Wipes the database -- BE CAREFUL!
    *
    * Useful in test suites.
    */
  def deleteAllIndices: Future[Unit]
}
