package org.overviewproject.database

import scala.concurrent.Future
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.tables.DocumentSetCreationJobs
import scala.concurrent.ExecutionContext

trait DocumentSetCreationJobDeleter extends SlickClient {

  def deleteByDocumentSet(documentSetId: Long): Future[Unit] = db { implicit session =>
    val documentSetCreationJob = DocumentSetCreationJobs.filter(_.documentSetId === documentSetId)

    val uploadedCsvOids = documentSetCreationJob.map(_.contentsOid).list.flatten

    uploadedCsvOids.map(deleteContent)
    
    documentSetCreationJob.delete
  }

  private def deleteContent(oid: Long) = {
    val csvUploadLocation = s"pglo:$oid"

    blobStorage.delete(csvUploadLocation)
  }

  protected val blobStorage: BlobStorage
}


object DocumentSetCreationJobDeleter {
  def apply(implicit executionContext: ExecutionContext): DocumentSetCreationJobDeleter = new DocumentSetCreationJobDeleter with SlickSessionProvider {
    override implicit protected val executor = executionContext
    override protected val blobStorage = BlobStorage
  }
}