package org.overviewproject.database

import scala.concurrent.Future 
import scala.concurrent.ExecutionContext.Implicits.global
import scala.slick.lifted.AbstractTable
import scala.slick.jdbc.StaticQuery.interpolation
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.tables._

trait DocumentSetDeleter extends SlickClient {

  def delete(documentSetId: Long): Future[Unit] = db { implicit session =>
    val uploadedFileId = findUploadedFileId(documentSetId)

    deleteViews(documentSetId)
    deleteUserAddedData(documentSetId)
    deleteTrees(documentSetId)
    deleteCore(documentSetId)
    
    deleteUploadedFile(uploadedFileId)
  }

  private def findUploadedFileId(documentSetId: Long)(implicit session: Session): Option[Long] =
    DocumentSets.filter(_.id === documentSetId).map(_.uploadedFileId).firstOption.flatten

  // The minimal set of components, common to all document sets
  private def deleteCore(documentSetId: Long)(implicit session: Session): Unit = {
    val documentProcessingErrors = DocumentProcessingErrors.filter(_.documentSetId === documentSetId)
    val documentSetUser = DocumentSetUsers.filter(_.documentSetId === documentSetId)
    val documentSet = DocumentSets.filter(_.id === documentSetId)

    deleteDocumentsAndFiles(documentSetId)
    
    documentProcessingErrors.delete
    documentSetUser.delete
    documentSet.delete

  }

  // Artifacts added by the user interacting with the system
  private def deleteUserAddedData(documentSetId: Long)(implicit session: Session): Unit = {
    val tags = Tags.filter(_.documentSetId === documentSetId)
    val documentTags = DocumentTags.filter(_.tagId in tags.map(_.id))
    
    documentTags.delete
    tags.delete
  }

  
  // Artifacts added by clustering
  private def deleteTrees(documentSetId: Long)(implicit session: Session): Unit = {
	val trees = Trees.filter(_.documentSetId === documentSetId)
	val nodes = Nodes.filter(_.rootId in trees.map(_.rootNodeId))
	val nodeDocuments = NodeDocuments.filter(_.nodeId in nodes.map(_.id))
	
	nodeDocuments.delete
	trees.delete
	nodes.delete
  }
  
  private def deleteUploadedFile(uploadedFileId: Option[Long])(implicit session: Session): Unit =
    uploadedFileId.map { uid => UploadedFiles.filter(_.id === uid).delete }

  
  private def deleteDocuments(documentSetId: Long)(implicit session: Session): Unit = {
    val documents = Documents.filter(_.documentSetId === documentSetId)
    documents.delete
  }
  
  // Decrement reference counts on Files
  // Assume something else deletes them when reference count is 0
  // Delete documents in the same transaction to avoid race conditions
  private def deleteDocumentsAndFiles(documentSetId: Long)(implicit session: Session): Unit = {
   val fileAndDocumentDeletion = sqlu"""
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
        
    fileAndDocumentDeletion.execute
  }

  // Components added with the API
  private def deleteViews(documentSetId: Long)(implicit session: Session): Unit = {
    val apiTokens = ApiTokens.filter(_.documentSetId === documentSetId)
    val views = Views.filter(_.documentSetId === documentSetId)
    val stores = Stores.filter(_.apiToken in apiTokens.map(_.token))
    val storeObjects = StoreObjects.filter(_.storeId in stores.map(_.id))
    val documentStoreObjects = DocumentStoreObjects.filter(_.storeObjectId in storeObjects.map(_.id))
    
    documentStoreObjects.delete
    storeObjects.delete
    stores.delete
    views.delete
    apiTokens.delete
  }
}

object DocumentSetDeleter {
  
  def apply(): DocumentSetDeleter = new DocumentSetDeleter with SlickSessionProvider 

    
}
