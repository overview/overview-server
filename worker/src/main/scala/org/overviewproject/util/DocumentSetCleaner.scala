package org.overviewproject.util

import scala.concurrent.Future

import org.overviewproject.models.tables.Documents

trait DocumentSetCleaner extends JobUpdater {
  import databaseApi._

  def deleteDocuments(documentSetId: Long): Future[Unit] = {
    database.delete(Documents.filter(_.documentSetId === documentSetId))
  } 
}
