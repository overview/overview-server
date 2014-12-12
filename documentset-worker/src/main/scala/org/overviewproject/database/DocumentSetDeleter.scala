package org.overviewproject.database

import scala.concurrent.Future
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.tables.{ Documents, DocumentSets, DocumentSetUsers, SearchResults, Tags, UploadedFiles }
import scala.slick.lifted.AbstractTable

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
    
    tags.delete
    searchResults.delete
    
    documents.delete
    documentSetUser.delete
    documentSet.delete

    uploadedFiles.delete
    
  }
  
}

