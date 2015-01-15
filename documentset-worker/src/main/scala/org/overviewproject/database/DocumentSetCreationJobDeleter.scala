package org.overviewproject.database

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.DocumentSetCreationJobState
import org.overviewproject.models.tables.DocumentSetCreationJobs


trait DocumentSetCreationJobDeleter extends SlickClient {

  def deleteByDocumentSet(documentSetId: Long): Future[Unit] = db { implicit session =>
    val documentSetCreationJob = DocumentSetCreationJobs.filter(_.documentSetId === documentSetId)

    val uploadedCsvOids = documentSetCreationJob.map(_.contentsOid).list.flatten

    uploadedCsvOids.map(deleteContent)

    documentSetCreationJob.delete
  }

  def delete(id: Long): Future[Unit] =
    db { implicit session =>
      val documentSetCreationJob = DocumentSetCreationJobs.filter(_.id === id)

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
  def apply(): DocumentSetCreationJobDeleter = new DocumentSetCreationJobDeleter with SlickSessionProvider {
    override protected val blobStorage = BlobStorage
  }
}