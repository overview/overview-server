package org.overviewproject.models

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json

class VizObjectSpec extends Specification {
  "VizObject" should {
    "#build" should {
      "build a VizObject" in {
        val vizId = 1L
        val vizObjectId = 2L
        val attributes = VizObject.CreateAttributes(
          indexedLong=Some(4L),
          indexedString=Some("foo"),
          json=Json.obj("foo" -> "bar")
        )
        val vizObject = VizObject.build(vizObjectId, vizId, attributes)
        vizObject.id must beEqualTo(vizObjectId)
        vizObject.vizId must beEqualTo(vizId)
        vizObject.indexedLong must beSome(4L)
        vizObject.indexedString must beSome("foo")
        vizObject.json must beEqualTo(Json.obj("foo" -> "bar"))
      }
    }

    "#update" should {
      "update attributes" in {
        val vizId = 1L
        val vizObjectId = 2L
        val vizObject = VizObject(
          id=vizObjectId,
          vizId=vizId,
          indexedLong=Some(4L),
          indexedString=Some("foo"),
          json=Json.obj("foo" -> "bar")
        )
        val attributes = VizObject.UpdateAttributes(
          indexedLong=Some(6L),
          indexedString=None,
          json=Json.obj("new foo" -> "new bar")
        )
        val newVizObject = vizObject.update(attributes)

        newVizObject must beEqualTo(VizObject(
          id=vizObjectId,
          vizId=vizId,
          indexedLong=Some(6L),
          indexedString=None,
          json=Json.obj("new foo" -> "new bar")
        ))
      }
    }
  }
}
