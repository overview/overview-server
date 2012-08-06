package models

import java.sql.Connection

trait PersistentTag {
  val id: Long
}

object PersistentTag {
  
  def findOrCreateByName(name: String, documentSetId: Long,
		  				 loader: TagLoader = new TagLoader(), 
		  				 saver: TagSaver = new TagSaver())
  						(implicit c: Connection) : PersistentTag = {
    val tagId = loader.loadByName(name) match {
      case Some(id) => id
      case None => saver.save(name, documentSetId).get 
    }
    
    new PersistentTagImpl(tagId, name, loader, saver)
  }
  
  private class PersistentTagImpl(tagId: Long, name: String,
		  						  loader: TagLoader, saver: TagSaver) extends PersistentTag {
    val id = tagId
  }
}