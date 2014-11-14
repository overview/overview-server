package models.archive

import java.io.InputStream

class PageViewInfoSpec extends ViewInfoSpecification {

  "PageViewInfo" should {

    "create entries for PageViews" in new PageViewInfoContext {
      matchesEntryParams(pageTitle + Pdf, size, pageId)(entry)
    }

    "remove pdf extension from filename with page" in new PdfPageContext {
      matchesEntryParams(s"$baseName $pageDescriptor" + Pdf, size, pageId)(entry)
    }

  }

  trait PageViewInfoContext extends ArchiveEntryFactoryContext {
    val pageNumber = 5
    val pageDescriptor = s"p$pageNumber"
    val pageTitle = s"$cleanName $pageDescriptor"

    val pageId = 1l

    val pageViewInfo = PageViewInfo(originalName, pageNumber, pageId, size)

    val viewInfo = new TestPageViewInfo(originalName, pageNumber, pageId, size)
    val entry = viewInfo.archiveEntry
    
    override def streamWasCreatedFromId(id: Long) =
      there was one(viewInfo.mockStorage).pageDataStream(pageId)

  }

  trait PdfPageContext extends PageViewInfoContext {
    def baseName = "file"
    override def originalName = baseName + Pdf
  }

  class TestPageViewInfo(title: String, pageNumber: Int, pageId: Long, size: Long)
      extends PageViewInfo(title, pageNumber, pageId, size) {
    override protected val storage = smartMock[Storage]
    
    val mockStorage = storage
    storage.pageDataStream(pageId) returns Some(smartMock[InputStream])
  }
}