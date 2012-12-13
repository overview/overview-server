package models

import models.orm.DocumentSetCreationJobState._

trait OverviewDocumentSetCreationJob {
  val documentSetId: Long
  val state: DocumentSetCreationJobState
}

object OverviewDocumentSetCreationJob {
  
  
  def apply(documentSet: OverviewDocumentSet): OverviewDocumentSetCreationJob = {
    new OverviewDocumentSetCreationJobImpl(documentSet.id)
  }
  
  private class OverviewDocumentSetCreationJobImpl(val documentSetId: Long) extends OverviewDocumentSetCreationJob {
    val state: DocumentSetCreationJobState = NotStarted
  }
}