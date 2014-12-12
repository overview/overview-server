package org.overviewproject.database

import scala.concurrent.Future
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.tables._
import scala.slick.lifted.AbstractTable
import scala.slick.jdbc.StaticQuery.interpolation

trait DocumentSetDeleter {

  protected def db[A](block: Session => A): Future[A]

  def delete(documentSetId: Long): Future[Unit] = db { implicit session =>
    val uploadedFileId = findUploadedFileId(documentSetId)

    releaseFiles(documentSetId)

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
    val documents = Documents.filter(_.documentSetId === documentSetId)
    val documentSetUser = DocumentSetUsers.filter(_.documentSetId === documentSetId)
    val documentSet = DocumentSets.filter(_.id === documentSetId)

    documentProcessingErrors.delete
    documents.delete
    documentSetUser.delete
    documentSet.delete

  }

  // Artifacts added by the user interacting with the system
  private def deleteUserAddedData(documentSetId: Long)(implicit session: Session): Unit = {
    val tags = Tags.filter(_.documentSetId === documentSetId)
    val searchResults = SearchResults.filter(_.documentSetId === documentSetId)

    tags.delete
    searchResults.delete
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

  // Decrement reference counts on Files and Pages
  // Assume something else deletes them when reference count is 0
  // Use raw sql because Slick does not support decrement 
  private def releaseFiles(documentSetId: Long)(implicit session: Session): Unit = {
    val fileReferences = sqlu"""
      UPDATE file SET reference_count = reference_count - 1
      WHERE reference_count > 0 AND id IN 
        (SELECT file_id FROM document where document_set_id  = $documentSetId)
     """

    val pageReferences = sqlu"""
       UPDATE page SET reference_count = reference_count - 1
       WHERE reference_count > 0 AND file_id IN
        (SELECT file_id FROM document where document_set_id  = $documentSetId)
     """

    fileReferences.execute
    pageReferences.execute

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

