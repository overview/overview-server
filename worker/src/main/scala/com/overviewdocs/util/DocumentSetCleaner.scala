package com.overviewdocs.util

import scala.concurrent.Future

import com.overviewdocs.models.tables.Documents

trait DocumentSetCleaner extends JobUpdater {
  import database.api._

  def deleteDocuments(documentSetId: Long): Future[Unit] = {
    database.delete(Documents.filter(_.documentSetId === documentSetId))
  } 
}

object DocumentSetCleaner extends DocumentSetCleaner
