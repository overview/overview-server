package models

import scala.language.implicitConversions

/** Identifies a few documents out of an entire document set.
  *
  * The actual <em>Selection</em> is what points to a list of documents. A
  * <em>SelectionRequest</em> is, well, a request to create one.
  */
case class SelectionRequest(
  val documentSetId: Long,
  val nodeIds: Seq[Long],
  val tagIds: Seq[Long],
  val documentIds: Seq[Long],
  val searchResultIds: Seq[Long],
  val tagged: Option[Boolean]
)

object SelectionRequest {
  private val MagicUntaggedTagId = 0l
  
  class SeqLongParam(val ids: Seq[Long])

  implicit def stringToSeqLongParam(s: String) = new SeqLongParam(IdList.longs(s).ids)
  implicit def idsLongToSeqLongParam(ids: Seq[Long]) = new SeqLongParam(ids)

  def apply(documentSetId: Long) : SelectionRequest = {
    apply(documentSetId, Seq[Long](), Seq[Long](), Seq[Long](), Seq[Long](), None)
  }

  def apply(documentSetId: Long, nodeIds: SeqLongParam, tagIds: SeqLongParam, documentIds: SeqLongParam, 
      searchResultIds: SeqLongParam) : SelectionRequest = {
    if (tagIds.ids.contains(MagicUntaggedTagId)) {
      apply(documentSetId, nodeIds.ids, tagIds.ids.filterNot(_ == MagicUntaggedTagId),
    		documentIds.ids, searchResultIds.ids, Some(false))
    }
    else apply(documentSetId, nodeIds.ids, tagIds.ids, documentIds.ids, searchResultIds.ids, None)
  }
}
