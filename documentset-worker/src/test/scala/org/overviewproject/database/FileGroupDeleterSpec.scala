package org.overviewproject.database

import scala.slick.jdbc.JdbcBackend.Session

import org.overviewproject.models.tables.FileGroups
import org.overviewproject.test.{DbSpecification,SlickClientInSession}

class FileGroupDeleterSpec extends DbSpecification {

  "FileGroupDeleter" should {

    "mark file_group deleted" in new FileGroupScope {
      await(deleter.delete(fileGroup.id))

      import org.overviewproject.database.Slick.simple._
      FileGroups.filter(_.id === fileGroup.id).firstOption(session) must beSome.like {
        case f => f.deleted must beTrue
      }
    }
  }

  trait FileGroupScope extends DbScope {
    val fileGroup = factory.fileGroup()

    val deleter = new TestFileGroupDeleter(session)
  }

  class TestFileGroupDeleter(val session: Session) extends FileGroupDeleter with SlickClientInSession
}
