package controllers.backend

import scala.concurrent.Future
import com.overviewdocs.query.Query
import com.overviewdocs.searchindex.{ElasticSearchIndexClient, Highlight, IndexClient, Snippet}

/** Finds highlights of a search term in a document.
  */
trait HighlightBackend extends Backend {
  /** Lists all highlights of a given term in the document.
    *
    * @param documentSetId Document set ID (specifies where document is stored)
    * @param documentId Document ID
    * @param q Search string
    */
  def index(documentSetId: Long, documentId: Long, q: Query): Future[Seq[Highlight]]

  def index(documentSetId: Long, documentIds: Seq[Long], q: Query): Future[Map[Long, Seq[Snippet]]]
}

/** ElasticSearch-backed highlight backend.
  */
trait EsHighlightBackend extends HighlightBackend {
  val indexClient: IndexClient

  override def index(documentSetId: Long, documentIds: Seq[Long], q: Query): Future[Map[Long, Seq[Snippet]]] = {
    indexClient.highlights(documentSetId, documentIds, q)
  }

  override def index(documentSetId: Long, documentId: Long, q: Query) = {
    indexClient.highlight(documentSetId, documentId, q)
  }
}

object HighlightBackend extends EsHighlightBackend {
  override val indexClient = com.overviewdocs.searchindex.ElasticSearchIndexClient.singleton
}
