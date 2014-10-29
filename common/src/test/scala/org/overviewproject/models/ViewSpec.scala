package org.overviewproject.models

import java.sql.Timestamp
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class ViewSpec extends Specification {
  "View" should {
    "#build" should {
      trait BuildScope extends Scope {
        val attributes = View.CreateAttributes(
          url="http://example.org",
          apiToken="api-token",
          title="title"
        )
        val view = View.build(1L, 2L, attributes)
      }

      "build a View" in new BuildScope {
        view.id must beEqualTo(1L)
        view.documentSetId must beEqualTo(2L)
        view.url must beEqualTo("http://example.org")
        view.apiToken must beEqualTo("api-token")
        view.title must beEqualTo("title")
      }

      "set createdAt to the current time" in new BuildScope {
        view.createdAt.getTime must beCloseTo(scala.compat.Platform.currentTime, 1000)
      }
    }

    "#update" should {
      "update attributes" in {
        val view = View(
          1L,
          2L,
          "http://example.org",
          "api-token",
          "title",
          new Timestamp(1234L)
        )
        val attributes = View.UpdateAttributes(title = "new title")

        view.update(attributes) must beEqualTo(View(
          1L,
          2L,
          "http://example.org",
          "api-token",
          "new title",
          new Timestamp(1234L)
        ))
      }
    }
  }
}
