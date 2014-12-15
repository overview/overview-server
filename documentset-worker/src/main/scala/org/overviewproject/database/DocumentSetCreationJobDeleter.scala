package org.overviewproject.database

import scala.concurrent.Future
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.tables.DocumentSetCreationJobs


trait DocumentSetCreationJobDeleter extends SlickClient {

  def deleteByDocumentSet(documentSetId: Long): Future[Unit] = db { implicit session =>
    val documentSetCreationJob = DocumentSetCreationJobs.filter(_.documentSetId === documentSetId)

    documentSetCreationJob.delete
  }
}

