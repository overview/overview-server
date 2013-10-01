package org.overviewproject.database.orm.finders

import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.FileGroup
import org.overviewproject.tree.orm.FileJobState._
import java.util.UUID
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.database.orm.Schema
import org.overviewproject.tree.orm.GroupedFileUpload
import java.sql.Timestamp

class GroupedFileUploadFinderSpec extends DbSpecification {

  object FileGroupStore extends BaseStore(Schema.fileGroups)
  object GroupedFileUploadStore extends BaseStore(Schema.groupedFileUploads)
  
  step(setupDb)
  
  "GroupedFileUploadFinder" should {
    
    
    "count GroupedFileUploads" in new DbTestContext {
      val numberOfGroupedFileUploads = 10
      val fileGroup = FileGroupStore.insertOrUpdate(FileGroup("filegroup", "admin@overviewproject.org", Complete))
      
      val groupedFileUploads = Seq.tabulate(numberOfGroupedFileUploads)(n => GroupedFileUpload(
          fileGroup.id,
          UUID.randomUUID,
          "application/pdf",
          s"file$n",
          n * 1000,
          "2011-03-22",
          n * 1000,
          n))
      GroupedFileUploadStore.insertBatch(groupedFileUploads)
      
      GroupedFileUploadFinder.countsByFileGroup(fileGroup.id) must be equalTo(numberOfGroupedFileUploads)
    }
  }
  
  step(shutdownDb)
}