package org.overviewproject.jobhandler.filegroup.task.step

import org.overviewproject.models.Document

trait DocumentData {
  def toDocument(documentSetId: Long): Document
}