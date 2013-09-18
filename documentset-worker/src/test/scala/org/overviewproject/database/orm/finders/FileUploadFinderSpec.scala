package org.overviewproject.database.orm.finders

import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.FileGroup
import org.overviewproject.tree.orm.FileJobState._
import java.util.UUID
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.database.orm.Schema
import org.overviewproject.tree.orm.FileUpload
import java.sql.Timestamp

class FileUploadFinderSpec extends DbSpecification {

  object FileGroupStore extends BaseStore(Schema.fileGroups)
  object FileUploadStore extends BaseStore(Schema.fileUploads)
  
  step(setupDb)
  
  "FileUploadFinder" should {
    
    
    "count FileUploads" in new DbTestContext {
      val numberOfFileUploads = 10
      val fileGroup = FileGroupStore.insertOrUpdate(FileGroup(UUID.randomUUID, "admin@overviewproject.org", Complete))
      
      val fileUploads = Seq.tabulate(numberOfFileUploads)(n => FileUpload(
          fileGroup.id,
          UUID.randomUUID,
          s"filename=file$n",
          "application/pdf",
          n * 1000,
          new Timestamp(scala.compat.Platform.currentTime),
          n))
      FileUploadStore.insertBatch(fileUploads)
      
      FileUploadFinder.countsByFileGroup(fileGroup.id) must be equalTo(numberOfFileUploads)
    }
  }
  
  step(shutdownDb)
}