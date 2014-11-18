package models.archive

import org.specs2.matcher.MatchResult
import java.io.InputStream
import org.specs2.mock.Mockito

class TextViewInfoSpec extends ViewInfoSpecification with Mockito {

  "TextViewInfo" should {

    "use suppliedId as filename" in new TextViewInfoContext {
      entry must matchParameters(suppliedId + Txt, size, documentId)
    }

    "use title as filename if there is no suppliedId" in new TitleContext {
      entry must matchParameters(title + Txt, size, documentId)
    }
    
    "use id as filename if there is no title or suppliedId" in new DocumentIdContext {
      entry must matchParameters(documentId + Txt, size, documentId)
    }
    
    "use page number in filename if present" in new PageContext {
      entry must matchParameters(s"$title p${pageNumberValue}$Txt", size, documentId)
    }
  }

  trait TextViewInfoContext extends ArchiveEntryFactoryContext {
    val Txt = ".txt"

    val documentId = 123l
      
    def suppliedId: String = "suppliedId"
    def title: String = "titleValue"
    def pageNumber: Option[Int] = None
    
    val textViewInfo = new TestTextViewInfo(suppliedId, title, documentId, pageNumber, size)
    val entry = textViewInfo.archiveEntry

    override def streamWasCreatedFromId(id: Long): MatchResult[Any] =
      there was one(textViewInfo.mockStorage).textInputStream(id)
  }

  trait TitleContext extends TextViewInfoContext {
    override def suppliedId: String = ""
  }
  
  trait DocumentIdContext extends TitleContext {
    override def title: String = ""
  }

  trait PageContext extends TitleContext {
    def pageNumberValue = 1105
    override def pageNumber = Some(pageNumberValue)
  }
  
  class TestTextViewInfo(suppliedId: String, title: String, documentId: Long, pageNumber: Option[Int], size: Long)
      extends TextViewInfo(suppliedId, title, documentId, pageNumber, size) {

    override protected val storage = smartMock[Storage]
    val mockStorage = storage
  }

}