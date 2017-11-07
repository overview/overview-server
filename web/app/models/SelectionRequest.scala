package models

import scala.collection.immutable

import com.overviewdocs.query.Query

/** Identifies a few documents out of an entire document set.
  *
  * The actual <em>Selection</em> is what points to a list of documents. A
  * <em>SelectionRequest</em> is, well, a request to create one.
  */
case class SelectionRequest(
  val documentSetId: Long,
  val nodeIds: immutable.Seq[Long] = Vector(),
  val tagIds: immutable.Seq[Long] = Vector(),
  val documentIds: immutable.Seq[Long] = Vector(),
  val documentIdsBitSet: Option[immutable.BitSet] = None,
  val storeObjectIds: immutable.Seq[Long] = Vector(),
  val tagged: Option[Boolean] = None,
  val q: Option[Query] = None,
  val tagOperation: SelectionRequest.TagOperation = SelectionRequest.TagOperation.Any,
  val sortByMetadataField: Option[String] = None
) {
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
