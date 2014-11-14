package models.archive

import org.specs2.matcher.MatchResult
import java.io.InputStream
import org.specs2.mock.Mockito

class TextViewInfoSpec extends ViewInfoSpecification with Mockito {

  "TextViewInfo" should {

    "use suppliedId as filename" in new TextViewInfoContext {
      entry must matchParameters(suppliedIdValue + Txt, size, documentId)
    }

    "use title as filename if there is no suppliedId" in new TitleContext {
      entry must matchParameters(titleValue + Txt, size, documentId)
    }
    
    "use id as filename if there is no title or suppliedId" in new DocumentIdContext {
      entry must matchParameters(documentId + Txt, size, documentId)
    }
  }

  trait TextViewInfoContext extends ArchiveEntryFactoryContext {
    val Txt = ".txt"

    val documentId = 123l
    val suppliedIdValue = "suppliedId"
    val titleValue = "titleValue"
      
    def suppliedId: Option[String] = Some(suppliedIdValue)
    def title: Option[String] = Some(titleValue)

    val textViewInfo = new TestTextViewInfo(suppliedId, title, documentId, size)
    val entry = textViewInfo.archiveEntry

    override def streamWasCreatedFromId(id: Long): MatchResult[Any] =
      id must be equalTo (documentId)
  }

  trait TitleContext extends TextViewInfoContext {
    override def suppliedId: Option[String] = None
  }
  
  trait DocumentIdContext extends TitleContext {
    override def title: Option[String] = None
  }

  class TestTextViewInfo(suppliedId: Option[String], title: Option[String], documentId: Long, size: Long)
      extends TextViewInfo(suppliedId, title, documentId, size) {

    override protected def textInputStream(documentId: Long)(): InputStream = smartMock[InputStream]
  }

}