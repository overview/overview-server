package org.overviewproject.database.orm.stores


import org.overviewproject.database.orm.Schema
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.tree.orm.File
import org.overviewproject.database.orm.FileGroupFile

object FileStore extends BaseStore(Schema.files)  {
  
  private object FileGroupFileStore extends BaseStore(Schema.fileGroupFiles)
  
  def insertWithFileGroup(fileGroupId: Long, file: File): File = {
    val savedFile = insertOrUpdate(file)

    FileGroupFileStore.insertOrUpdate(FileGroupFile(fileGroupId, savedFile.id))
    
    savedFile
  }
}