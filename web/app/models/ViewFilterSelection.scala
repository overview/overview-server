package models

import scala.collection.immutable

/** A client request to filter a selection by a remote-server response.
  *
  * It depends on a View existing in the database with a ViewFilter.
  */
case class ViewFilterSelection(
  viewId: Long,
  ids: immutable.Seq[String],
  operation: ViewFilterSelection.Operation
)

object ViewFilterSelection {
  sealed trait Operation
  object Operation {
    case object Any extends Operation
    case object All extends Operation
    case object None extends Operation
  }
}
