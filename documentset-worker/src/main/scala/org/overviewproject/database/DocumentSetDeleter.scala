package org.overviewproject.database

import scala.concurrent.Future 
import scala.concurrent.ExecutionContext.Implicits.global

import org.overviewproject.models.tables._

trait DocumentSetDeleter extends HasDatabase {
  import databaseApi._

  def delete(documentSetId: Long): Future[Unit] = {
    database.run(for {
      uploadedFileId <- findUploadedFileId(documentSetId)
      _ <- deleteViews(documentSetId)
      _ <- deleteUserAddedData(documentSetId)
      _ <- deleteTrees(documentSetId)
      _ <- deleteCore(documentSetId)
      _ <- deleteUploadedFile(uploadedFileId)
    } yield ())
  }

  private def findUploadedFileId(documentSetId: Long): DBIO[Option[Long]] = {
    DocumentSets
      .filter(_.id === documentSetId)
      .map(_.uploadedFileId)
      .result.headOption // DBIO[Option[Option[Long]]]
      .map(_.flatten)
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
  private def deleteTrees(documentSetId: Long): DBIO[Unit] = {
    val trees = Trees.filter(_.documentSetId === documentSetId)
    val nodes = Nodes.filter(_.rootId in trees.map(_.rootNodeId))
    val nodeDocuments = NodeDocuments.filter(_.nodeId in nodes.map(_.id))

    for {
      _ <- nodeDocuments.delete
      _ <- trees.delete
      _ <- nodes.delete
    } yield ()
  }
  
  private def deleteUploadedFile(maybeUploadedFileId: Option[Long]): DBIO[Unit] = {
    val delete = maybeUploadedFileId match {
      case None => DBIO.successful(0)
      case Some(uploadedFileId) => UploadedFiles.filter(_.id === uploadedFileId).delete
    }
    delete.map(_ => ()) // delete returns an Int
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

object DocumentSetDeleter {
  def apply(): DocumentSetDeleter = new DocumentSetDeleter with DatabaseProvider 
}
