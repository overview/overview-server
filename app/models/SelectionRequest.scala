package models

/** Identifies a few documents out of an entire document set.
  *
  * The actual <em>Selection</em> is what points to a list of documents. A
  * <em>SelectionRequest</em> is, well, a request to create one.
  */
case class SelectionRequest(
  val documentSetId: Long,
  val nodeIds: Seq[Long] = Seq(),
  val tagIds: Seq[Long] = Seq(),
  val documentIds: Seq[Long] = Seq(),
  val searchResultIds: Seq[Long] = Seq(),
  val vizObjectIds: Seq[Long] = Seq(),
  val tagged: Option[Boolean] = None,
  val q: String = ""
)
