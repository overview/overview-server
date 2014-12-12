package org.overviewproject.database

import scala.concurrent.Future
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.tables._
import scala.slick.lifted.AbstractTable
import scala.slick.jdbc.StaticQuery.interpolation

trait DocumentSetDeleter {

  protected def db[A](block: Session => A): Future[A]
  
  def delete(documentSetId: Long): Future[Unit] = db { implicit session => 
    val documents = Documents.filter(_.documentSetId === documentSetId) 
    val documentSetUser = DocumentSetUsers.filter(_.documentSetId === documentSetId)
    val documentSet = DocumentSets.filter(_.id === documentSetId) 

    val uploadedFileId = UploadedFiles.filter(_.id in documentSet.map(_.uploadedFileId)).map(_.id).firstOption
    val uploadedFiles = UploadedFiles.filter(_.id === uploadedFileId)
    
    val tags = Tags.filter(_.documentSetId === documentSetId)
    val searchResults = SearchResults.filter(_.documentSetId === documentSetId)
    
    
    val fileReferences = sqlu"""
      UPDATE file SET reference_count = reference_count - 1
      WHERE reference_count > 0 AND id IN 
        (SELECT file_id FROM document where document_set_id  = $documentSetId)
     """
     fileReferences.execute 
     
     val pageReferences = sqlu"""
       UPDATE page SET reference_count = reference_count - 1
       WHERE reference_count > 0 AND file_id IN
        (SELECT file_id FROM document where document_set_id  = $documentSetId)
     """
    pageReferences.execute
    
    tags.delete
    searchResults.delete
    
    documents.delete
    documentSetUser.delete
    documentSet.delete

    uploadedFiles.delete
    
  }
  
}

