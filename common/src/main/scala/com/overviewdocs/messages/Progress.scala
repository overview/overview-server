package com.overviewdocs.messages

object Progress {
  /** Part of the "sorting" sequence of progress events.
    *
    * Sorting progress consists of zero or more Sorting messages, then a final
    * SortDone message.
    *
    * Before adding complexity to these messages, consider the complexity. It
    * may be simpler to add more information to the sideband the
    * `document_id_list` table.
    */
  sealed trait SortProgress
  case class Sorting(progress: Double) extends SortProgress
  case object SortDone extends SortProgress
}
