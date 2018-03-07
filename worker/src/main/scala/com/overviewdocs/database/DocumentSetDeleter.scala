package com.overviewdocs.database

import scala.collection.immutable
import scala.concurrent.Future 
import scala.concurrent.ExecutionContext.Implicits.global

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.models.tables._
import com.overviewdocs.searchindex.{IndexClient,LuceneIndexClient}

trait DocumentSetDeleter extends HasDatabase {
  protected val indexClient: IndexClient
  protected val blobStorage: BlobStorage

  import database.api._

  def delete(documentSetId: Long): Future[Unit] = {
    val indexFuture = indexClient.removeDocumentSet(documentSetId)

    database.run(for {
      _ <- deleteViews(documentSetId)
      _ <- deleteUserAddedData(documentSetId)
      _ <- deleteTrees(documentSetId)
      _ <- deleteCloneJobs(documentSetId)
      _ <- deleteCsvImports(documentSetId)
      _ <- deleteDocumentCloudImports(documentSetId)
      _ <- deleteDocumentIdLists(documentSetId)
      _ <- DBIO.from(indexFuture) // Ensure it's out of ElasticSearch before deleting DocumentSet, so restart resumes the index-delete
      _ <- deleteCore(documentSetId)
    } yield ())
  }

  private def deleteCloneJobs(documentSetId: Long): DBIO[Unit] = {
    CloneJobs.filter(_.destinationDocumentSetId === documentSetId).delete.map(_ => ())
  }

  private def deleteCsvImports(documentSetId: Long): DBIO[Unit] = {
    val q = CsvImports.filter(_.documentSetId === documentSetId)

    (for {
      loids: Seq[Long] <- q.map(_.loid).result
      _ <- DBIO.seq(loids.map(database.largeObjectManager.unlink _): _*)
      _ <- q.delete
    } yield ()).transactionally
  }

  private def deleteDocumentCloudImports(documentSetId: Long): DBIO[Int] = {
    sqlu"""
      WITH imports AS (
        SELECT id
        FROM document_cloud_import
        WHERE document_set_id = $documentSetId
      ), delete1 AS (
        DELETE FROM document_cloud_import_id_list
        WHERE document_cloud_import_id IN (SELECT id FROM imports)
      )
      DELETE FROM document_cloud_import
      WHERE id IN (SELECT id FROM imports)
    """
  }

  private def deleteDocumentIdLists(documentSetId: Long): DBIO[Unit] = {
    DocumentIdLists.filter(_.documentSetId === documentSetId.toInt).delete.map(_ => ())
  }

  private def deleteFile2s(documentSetId: Long): Future[Unit] = {
    val toDeleteQ = sql"""
      WITH root_ids AS (
        SELECT file2_id
        FROM document_set_file2
        WHERE document_set_id = ${documentSetId}
          AND NOT EXISTS (
            SELECT 1
            FROM document_set_file2 dsf2
            WHERE dsf2.document_set_id <> ${documentSetId}
              AND dsf2.file2_id = document_set_file2.file2_id
          )
      )
      SELECT id, blob_location, thumbnail_blob_location
      FROM file2
      WHERE id IN (SELECT file2_id FROM root_ids) OR root_file2_id IN (SELECT file2_id FROM root_ids)
    """.as[(Long, Option[String], Option[String])]

    def deleteSql(file2Ids: immutable.Seq[Long]) = sqlu"""
      WITH delete1 AS (DELETE FROM document_set_file2 WHERE document_set_id = ${documentSetId})
      DELETE FROM file2 WHERE id = ANY(${file2Ids})
    """

    for {
      toDelete <- database.run(toDeleteQ)
      blobLocations: immutable.Seq[String] = toDelete.flatMap(_._2)
      thumbnailBlobLocations: immutable.Seq[String] = toDelete.flatMap(_._3)
      _ <- blobStorage.deleteMany(blobLocations ++ thumbnailBlobLocations)
      _ <- database.runUnit(deleteSql(toDelete.map(_._1)))
    } yield ()
  }

  // The minimal set of components, common to all document sets
  private def deleteCore(documentSetId: Long): DBIO[Unit] = {
    for {
      _ <- deleteDocumentsAndFiles(documentSetId)
      _ <- DocumentProcessingErrors.filter(_.documentSetId === documentSetId).delete
      _ <- DBIO.from(deleteFile2s(documentSetId))
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
  override protected val indexClient = LuceneIndexClient.onDiskSingleton
  override protected val blobStorage = BlobStorage
}
