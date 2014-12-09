package models.archive

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class TextViewInfoSpec extends Specification {

  "TextViewInfo" should {

    "use suppliedId as filename" in new TextViewInfoContext {
      entry.name must beEqualTo(suppliedId + ".txt")
    }

    "set size" in new TextViewInfoContext {
      entry.size must beEqualTo(size)
    }

    "use title as filename if there is no suppliedId" in new TextViewInfoContext {
      override val suppliedId: String = ""
      entry.name must beEqualTo(title + ".txt")
    }
    
    "use id as filename if there is no title or suppliedId" in new TextViewInfoContext {
      override val suppliedId: String = ""
      override val title: String = ""
      entry.name must beEqualTo(documentId + ".txt")
    }
    
    "use page number in filename if present" in new TextViewInfoContext {
      override val suppliedId: String = ""
      override val pageNumber = Some(1105)
      entry.name must beEqualTo(title + " p1105.txt")
    }
  }

  trait TextViewInfoContext extends Scope {
    val documentId: Long = 123L
    val suppliedId: String = "suppliedId"
    val title: String = "titleValue"
    val pageNumber: Option[Int] = None
    val size: Long = 123L

    def textViewInfo = new TestTextViewInfo(suppliedId, title, documentId, pageNumber, size)
    def entry = textViewInfo.archiveEntry
  }
  
  class TestTextViewInfo(suppliedId: String, title: String, documentId: Long, pageNumber: Option[Int], size: Long)
    extends TextViewInfo(suppliedId, title, documentId, pageNumber, size) {

    override def stream = ???
  }
}
