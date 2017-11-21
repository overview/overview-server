package com.overviewdocs.models

import java.sql.Timestamp
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class ViewSpec extends Specification {
  "View" should {
    "#update" should {
      "update attributes" in {
        val view = View(
          1L,
          2L,
          "http://example.org",
          None,
          "api-token",
          "title",
          None,
          new Timestamp(1234L)
        )
        val attributes = View.UpdateAttributes(title = "new title")

        view.update(attributes) must beEqualTo(View(
          1L,
          2L,
          "http://example.org",
          None,
          "api-token",
          "new title",
          None,
          new Timestamp(1234L)
        ))
      }
    }
  }
}
