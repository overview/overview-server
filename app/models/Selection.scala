package models

import scala.language.implicitConversions

/**
 * 
 */
case class Selection(
  val documentSetId: Long,
  val nodeIds: Seq[Long],
  val tagIds: Seq[Long],
  val documentIds: Seq[Long],
  val searchResultIds: Seq[Long],
  val untagged: Boolean
)

object Selection {
  private val MagicUntaggedTagId = 0l
  
  class SeqLongParam(val ids: Seq[Long])

  implicit def stringToSeqLongParam(s: String) = new SeqLongParam(IdList.longs(s).ids)
  implicit def idsLongToSeqLongParam(ids: Seq[Long]) = new SeqLongParam(ids)

  def apply(documentSetId: Long) : Selection = {
    apply(documentSetId, Seq[Long](), Seq[Long](), Seq[Long](), Seq[Long](), false)
  }

  def apply(documentSetId: Long, nodeIds: SeqLongParam, tagIds: SeqLongParam, documentIds: SeqLongParam, 
      searchResultIds: SeqLongParam, untagged: Boolean) : Selection = {
    if (tagIds.ids.contains(MagicUntaggedTagId)) {  
      apply(documentSetId, nodeIds.ids, tagIds.ids.filterNot(_ == MagicUntaggedTagId),
    		documentIds.ids, searchResultIds.ids, true)
    }
    else apply(documentSetId, nodeIds.ids, tagIds.ids, documentIds.ids, searchResultIds.ids, untagged)
  }
}
