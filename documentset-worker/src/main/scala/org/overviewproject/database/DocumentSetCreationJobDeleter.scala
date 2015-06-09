package org.overviewproject.database

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.models.{DocumentSetCreationJob,DocumentSetCreationJobState}
import org.overviewproject.models.tables.DocumentSetCreationJobs

trait DocumentSetCreationJobDeleter extends HasDatabase {
  import databaseApi._

  def deleteByDocumentSet(documentSetId: Long): Future[Unit] = {
    deleteJobs(DocumentSetCreationJobs.filter(_.documentSetId === documentSetId))
  }

  def delete(id: Long): Future[Unit] = {
    deleteJobs(DocumentSetCreationJobs.filter(_.id === id))
  }

  private def deleteJobs(query: Rep[Seq[DocumentSetCreationJob]]) = {
    database.runUnit(for {
      jobs <- query.result
      _ <- DBIO.from(deleteContent(jobs.map(_.contentsOid).flatten))
      _ <- DocumentSetCreationJobs.filter(_.id inSet jobs.map(_.id)).delete
    } yield ())
  }

  private def deleteContent(oids: Seq[Long]) = {
    val locations = oids.map(oid => s"pglo:$oid")
    blobStorage.deleteMany(locations)
  }

  protected val blobStorage: BlobStorage
}

object DocumentSetCreationJobDeleter {
  def apply(): DocumentSetCreationJobDeleter = new DocumentSetCreationJobDeleter with DatabaseProvider {
    override protected val blobStorage = BlobStorage
  }
}
