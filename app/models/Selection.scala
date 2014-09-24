package models

import java.util.{Date,UUID}

case class Selection(
  id: UUID,
  timestamp: Date,
  request: SelectionRequest,
  documentIds: Seq[Long]
)

object Selection {
  def apply(request: SelectionRequest, documentIds: Seq[Long]): Selection = Selection(
    UUID.randomUUID(),
    new Date(),
    request,
    documentIds
  )
}
