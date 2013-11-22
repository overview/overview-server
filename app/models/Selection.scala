package models

import scala.language.implicitConversions

case class Selection(
  val documentSetId: Long,
  val nodeIds: Seq[Long],
  val tagIds: Seq[Long],
  val documentIds: Seq[Long],
  val searchResultIds: Seq[Long]
)

object Selection {
  class SeqLongParam(val ids: Seq[Long])

  implicit def stringToSeqLongParam(s: String) = new SeqLongParam(IdList.longs(s).ids)
  implicit def idsLongToSeqLongParam(ids: Seq[Long]) = new SeqLongParam(ids)

  def apply(documentSetId: Long) : Selection = {
    apply(documentSetId, Seq[Long](), Seq[Long](), Seq[Long](), Seq[Long]())
  }

  def apply(documentSetId: Long, nodeIds: SeqLongParam, tagIds: SeqLongParam, documentIds: SeqLongParam, searchResultIds: SeqLongParam) : Selection = {
    apply(documentSetId, nodeIds.ids, tagIds.ids, documentIds.ids, searchResultIds.ids)
  }
}
