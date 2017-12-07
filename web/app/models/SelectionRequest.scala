package models

import scala.collection.immutable

import com.overviewdocs.query.Query

/** Identifies a few documents out of an entire document set.
  *
  * The actual <em>Selection</em> is what points to a list of documents. A
  * <em>SelectionRequest</em> is, well, a request to create one.
  */
case class SelectionRequest(
  /** DocumentSet ID */
  documentSetId: Long,

  /** Node IDs. TODO: nix these and use a ViewFilter. */
  nodeIds: immutable.Seq[Long] = Vector(),

  /** Tag IDs. Empty means all documents. */
  tagIds: immutable.Seq[Long] = Vector(),

  /** Document IDs. Empty means all documents. */
  documentIds: immutable.Seq[Long] = Vector(),

  /** Document IDs, by bitset. None means all documents. */
  documentIdsBitSet: Option[immutable.BitSet] = None,

  /** StoreObject IDs. Empty means all documents. TODO consider nixing StoreObjects? */
  storeObjectIds: immutable.Seq[Long] = Vector(),

  /** "any tag" specifier. TODO consider a front-end change to "select all" */
  tagged: Option[Boolean] = None,

  /** Whether to AND, OR or NOT tagIds. */
  tagOperation: SelectionRequest.TagOperation = SelectionRequest.TagOperation.Any,

  /** ViewFilter selections.
    *
    * Each comes from a separate View and will produce a set of document IDs.
    */
  viewFilterSelections: immutable.Seq[ViewFilterSelection] = Vector(),

  /** Full-text query. None means all documents. So does the empty string. TODO nix None. */
  q: Option[Query] = None,

  /** What to sort by. TODO add "reverse" here. */
  sortByMetadataField: Option[String] = None
) {
  /** Unique ID of this SelectionRequest, used for caching. */
  lazy val hash = hashCode

  /** Returns true iff this selection request is for all Documents in the
    * DocumentSet.
    */
  def isAll: Boolean = {
    (
      nodeIds.isEmpty
      && tagIds.isEmpty
      && documentIds.isEmpty
      && documentIdsBitSet.isEmpty
      && storeObjectIds.isEmpty
      && tagged.isEmpty
      && viewFilterSelections.isEmpty
      && q.isEmpty
    )
  }
}

object SelectionRequest {
  sealed trait TagOperation
  object TagOperation {
    case object Any extends TagOperation
    case object All extends TagOperation
    case object None extends TagOperation
  }
}
