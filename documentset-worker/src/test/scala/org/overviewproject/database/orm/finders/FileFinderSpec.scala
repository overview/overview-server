package org.overviewproject.database.orm.finders

import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.File
import java.util.UUID
import org.overviewproject.tree.orm.FileJobState._
import org.overviewproject.tree.orm.FileJobState
import java.sql.Timestamp
import org.overviewproject.database.orm.stores.FileStore
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.database.orm.Schema
import org.overviewproject.tree.orm.FileGroup

class FileFinderSpec extends DbSpecification {

  object FileGroupStore extends BaseStore(Schema.fileGroups)

  step(setupDb)

  "FileFinder" should {

    "count finished Files" in new DbTestContext {
      val fileGroup = FileGroupStore.insertOrUpdate(FileGroup(UUID.randomUUID, "admin@overviewproject.org", Complete))

      val numberOfCompleteFiles = 3
      val numberOfErrorFiles = 3
      val numberOfInProgressFiles = 3

      storeFiles(numberOfCompleteFiles, fileGroup.id, Complete)
      storeFiles(numberOfErrorFiles, fileGroup.id, Error)
      storeFiles(numberOfInProgressFiles, fileGroup.id, InProgress)

      FileFinder.byFinishedState(fileGroup.id).count must be equalTo(numberOfCompleteFiles + numberOfErrorFiles)
    }
  }

  step(shutdownDb)

  private def storeFiles(numberOfFiles: Int, fileGroupId: Long, state: FileJobState.Value): Unit =
    for (i <- 1 to numberOfFiles) {
      val file = File(fileGroupId, UUID.randomUUID, s"name-$i", "application/pdf", 1000 * i,
        state, s"text-$i", new Timestamp(scala.compat.Platform.currentTime))
      
        FileStore.insertOrUpdate(file)
    }

}