package models.archive

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class PageViewInfoSpec extends Specification {

  "PageViewInfo" should {

    "create entries for PageViews" in new PageViewInfoContext {
      override val pageNumber = 5
      override val originalName = "title"
      entry.name must beEqualTo("title p5.pdf")
    }

    "remove pdf extension from filename with page" in new PageViewInfoContext {
      override val pageNumber = 5
      override val originalName = "title.pdf"
      entry.name must beEqualTo("title p5.pdf")
    }

    "set size" in new PageViewInfoContext {
      override val size = 123L
      entry.size must beEqualTo(123L)
    }
  }

  trait PageViewInfoContext extends Scope {
    val pageNumber = 5
    val size = 123L
    val originalName = "title"
    val pageId = 1L

    val pageViewInfo = PageViewInfo(originalName, pageNumber, pageId, size)

    def viewInfo = new TestPageViewInfo(originalName, pageNumber, pageId, size)
    def entry = viewInfo.archiveEntry
  }

  class TestPageViewInfo(title: String, pageNumber: Int, pageId: Long, size: Long)
    extends PageViewInfo(title, pageNumber, pageId, size) {

    override def stream = ???
  }
}
