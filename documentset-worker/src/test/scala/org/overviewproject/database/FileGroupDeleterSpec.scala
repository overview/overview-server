package org.overviewproject.database


import org.overviewproject.models.tables.FileGroups
import org.overviewproject.database.Slick.simple._
import org.overviewproject.test.SlickSpecification
import org.overviewproject.test.SlickClientInSession

class FileGroupDeleterSpec extends SlickSpecification {

  "FileGroupDeleter" should {
    
    "mark file_group deleted" in new FileGroupScope {
      await(deleter.delete(fileGroup.id))
      
      FileGroups.filter(_.id === fileGroup.id).firstOption must beSome.like {
        case f => f.deleted must beTrue
      }
    }
    
    
  }
  
  trait FileGroupScope extends DbScope {
    val fileGroup = factory.fileGroup()
    
    val deleter = new TestFileGroupDeleter
  }
  
  class TestFileGroupDeleter(implicit val session: Session) extends FileGroupDeleter with SlickClientInSession
}