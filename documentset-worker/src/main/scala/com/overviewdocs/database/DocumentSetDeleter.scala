package com.overviewdocs.database

import scala.concurrent.Future 
import scala.concurrent.ExecutionContext.Implicits.global

import com.overviewdocs.models.tables._
import com.overviewdocs.searchindex.{IndexClient,TransportIndexClient}

trait DocumentSetDeleter extends HasDatabase {
  protected val indexClient: IndexClient

  import database.api._

  def delete(documentSetId: Long): Future[Unit] = {
    val indexFuture = indexClient.removeDocumentSet(documentSetId)

    database.run(for {
      _ <- deleteViews(documentSetId)
      _ <- deleteUserAddedData(documentSetId)
      _ <- deleteTrees(documentSetId)
      _ <- deleteJobs(documentSetId)
      _ <- deleteCsvImports(documentSetId)
      _ <- deleteCloneJobs(documentSetId)
      _ <- DBIO.from(indexFuture) // Ensure it's out of ElasticSearch before deleting DocumentSet, so restart resumes the index-delete
      _ <- deleteCore(documentSetId)
    } yield ())
  }

  private def deleteJobs(documentSetId: Long): DBIO[Unit] = {
    val q = DocumentSetCreationJobs
      .filter(j => j.documentSetId === documentSetId)
      .delete
    for { _ <- q } yield ()
  }

  private def deleteCsvImports(documentSetId: Long): DBIO[Unit] = {
    val q = CsvImports.filter(_.documentSetId === documentSetId)

    (for {
      loids: Seq[Long] <- q.map(_.loid).result
      _ <- DBIO.seq(loids.map(database.largeObjectManager.unlink _): _*)
      _ <- q.delete
    } yield ()).transactionally
  }

  private def deleteCloneJobs(documentSetId: Long): DBIO[Unit] = {
    CloneJobs.filter(_.destinationDocumentSetId === documentSetId).delete.map(_ => ())
  }

  // The minimal set of components, common to all document sets
  private def deleteCore(documentSetId: Long): DBIO[Unit] = {
    for {
      _ <- deleteDocumentsAndFiles(documentSetId)
      _ <- DocumentProcessingErrors.filter(_.documentSetId === documentSetId).delete
      _ <- DocumentSetUsers.filter(_.documentSetId === documentSetId).delete
      _ <- DocumentSets.filter(_.id === documentSetId).delete
    } yield ()
  }

  // Artifacts added by the user interacting with the system
  private def deleteUserAddedData(documentSetId: Long): DBIO[Unit] = {
    val tags = Tags.filter(_.documentSetId === documentSetId)

    for {
      _ <- DocumentTags.filter(_.tagId in tags.map(_.id)).delete
      _ <- tags.delete
    } yield ()
  }

  // Artifacts added by clustering
  private def deleteTrees(documentSetId: Long): DBIO[Int] = {
    // Hand-crafted is waaaaay faster than Scala-style
    sqlu"""
      WITH
      root_node_ids AS (
        SELECT root_node_id AS id
        FROM tree
        WHERE document_set_id = $documentSetId
      ),
      node_ids AS (SELECT id FROM node WHERE root_id IN (SELECT id FROM root_node_ids)),
      delete1 AS (DELETE FROM node_document WHERE node_id IN (SELECT id FROM node_ids)),
      delete2 AS (DELETE FROM node WHERE id IN (SELECT id FROM node_ids))
      DELETE FROM tree WHERE document_set_id = $documentSetId
    """
  }

  // Decrement reference counts on Files
  // Assume something else deletes them when reference count is 0
  // Delete documents in the same transaction to avoid race conditions
  private def deleteDocumentsAndFiles(documentSetId: Long): DBIO[Int] = {
    sqlu"""
      WITH
       deleted_documents AS (
         DELETE FROM document
         WHERE document_set_id = $documentSetId
         RETURNING file_id
       ),
       file_ids AS (
         SELECT id
         FROM file
         WHERE id IN (SELECT DISTINCT file_id FROM deleted_documents)
         FOR UPDATE
       )
       UPDATE file
       SET reference_count = reference_count - 1
       WHERE id IN (SELECT id FROM file_ids)
       AND reference_count > 0
    """
  }

  // Components added with the API
  private def deleteViews(documentSetId: Long): DBIO[Unit] = {
    val apiTokens = ApiTokens.filter(_.documentSetId === documentSetId)
    val views = Views.filter(_.documentSetId === documentSetId)
    val stores = Stores.filter(_.apiToken in apiTokens.map(_.token))
    val storeObjects = StoreObjects.filter(_.storeId in stores.map(_.id))
    val documentStoreObjects = DocumentStoreObjects.filter(_.storeObjectId in storeObjects.map(_.id))

    for {
      _ <- documentStoreObjects.delete
      _ <- storeObjects.delete
      _ <- stores.delete
      _ <- views.delete
      _ <- apiTokens.delete
    } yield ()
  }
}

object DocumentSetDeleter extends DocumentSetDeleter {
  override protected val indexClient = TransportIndexClient.singleton
}
