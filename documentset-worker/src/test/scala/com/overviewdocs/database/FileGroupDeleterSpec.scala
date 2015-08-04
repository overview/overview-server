package com.overviewdocs.database

import com.overviewdocs.models.tables.FileGroups
import com.overviewdocs.test.DbSpecification

class FileGroupDeleterSpec extends DbSpecification {

  "FileGroupDeleter" should {

    "mark file_group deleted" in new DbScope {
      val fileGroup = factory.fileGroup()
      val deleter = FileGroupDeleter
      await(deleter.delete(fileGroup.id))

      import database.api._
      blockingDatabase.option(FileGroups.filter(_.id === fileGroup.id).map(_.deleted)) must beSome(true)
    }
  }
}
