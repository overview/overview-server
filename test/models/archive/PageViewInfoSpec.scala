package models.archive

import org.specs2.mutable.Specification

class PageViewInfoSpec extends Specification {
  "PageViewInfo" should {
    "create entries for PageViews" in {
      val info = PageViewInfo("title", 5, "foo:bar", 1)
      info.archiveEntry.name must beEqualTo("title p5.pdf")
    }

    "remove pdf extension from filename with page" in {
      val info = PageViewInfo("title.pdf", 5, "foo:bar", 1)
      info.archiveEntry.name must beEqualTo("title p5.pdf")
    }

    "set size" in {
      val info = PageViewInfo("a", 1, "b", 123)
      info.archiveEntry.size must beEqualTo(123)
    }
  }
}
