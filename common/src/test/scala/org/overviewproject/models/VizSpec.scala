package org.overviewproject.models

import java.sql.Timestamp
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json

class VizSpec extends Specification {
  "Viz" should {
    "#build" should {
      trait BuildScope extends Scope {
        val attributes = Viz.CreateAttributes(
          url="http://example.org",
          apiToken="api-token",
          title="title",
          json=Json.obj("foo" -> "bar")
        )
        val viz = Viz.build(1L, 2L, attributes)
      }

      "build a Viz" in new BuildScope {
        viz.id must beEqualTo(1L)
        viz.documentSetId must beEqualTo(2L)
        viz.url must beEqualTo("http://example.org")
        viz.apiToken must beEqualTo("api-token")
        viz.title must beEqualTo("title")
        viz.json must beEqualTo(Json.obj("foo" -> "bar"))
      }

      "set createdAt to the current time" in new BuildScope {
        viz.createdAt.getTime must beCloseTo(scala.compat.Platform.currentTime, 1000)
      }
    }

    "#update" should {
      "update attributes" in {
        val viz = Viz(
          1L,
          2L,
          "http://example.org",
          "api-token",
          "title",
          new Timestamp(1234L),
          Json.obj("foo" -> "bar")
        )
        val attributes = Viz.UpdateAttributes(
          title = "new title",
          json = Json.obj("new foo" -> "new bar")
        )

        viz.update(attributes) must beEqualTo(Viz(
          1L,
          2L,
          "http://example.org",
          "api-token",
          "new title",
          new Timestamp(1234L),
          Json.obj("new foo" -> "new bar")
        ))
      }
    }
  }
}
