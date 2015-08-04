package com.overviewdocs.jobhandler.filegroup.task.step

import com.overviewdocs.models.Document

trait DocumentData {
  def toDocument(documentSetId: Long, documentId: Long): Document
}