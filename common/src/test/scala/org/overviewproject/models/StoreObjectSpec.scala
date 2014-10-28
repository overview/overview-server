package org.overviewproject.models

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json

class StoreObjectSpec extends Specification {
  "StoreObject" should {
    "#update" should {
      "update attributes" in {
        val storeId = 1L
        val storeObjectId = 2L
        val storeObject = StoreObject(
          id=storeObjectId,
          storeId=storeId,
          indexedLong=Some(4L),
          indexedString=Some("foo"),
          json=Json.obj("foo" -> "bar")
        )
        val attributes = StoreObject.UpdateAttributes(
          indexedLong=Some(6L),
          indexedString=None,
          json=Json.obj("new foo" -> "new bar")
        )
        val newStoreObject = storeObject.update(attributes)

        newStoreObject must beEqualTo(StoreObject(
          id=storeObjectId,
          storeId=storeId,
          indexedLong=Some(6L),
          indexedString=None,
          json=Json.obj("new foo" -> "new bar")
        ))
      }
    }
  }
}
