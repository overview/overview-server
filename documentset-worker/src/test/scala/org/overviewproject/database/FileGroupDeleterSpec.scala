package org.overviewproject.database

import org.overviewproject.models.tables.FileGroups
import org.overviewproject.test.DbSpecification

class FileGroupDeleterSpec extends DbSpecification {

  "FileGroupDeleter" should {

    "mark file_group deleted" in new DbScope {
      val fileGroup = factory.fileGroup()
      val deleter = new FileGroupDeleter with DatabaseProvider
      await(deleter.delete(fileGroup.id))

      import databaseApi._
      blockingDatabase.option(FileGroups.filter(_.id === fileGroup.id).map(_.deleted)) must beSome(true)
    }
  }
}
