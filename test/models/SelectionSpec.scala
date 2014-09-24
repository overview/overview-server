package models

import java.util.Date

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class SelectionSpec extends Specification {
  "assign a date" in {
    val date1 = new Date()
    val request = SelectionRequest(1L)
    val selection = Selection(request, Seq(1L, 2L, 3L))
    val date2 = new Date()

    selection.timestamp must beGreaterThanOrEqualTo(date1)
    selection.timestamp must beLessThanOrEqualTo(date2)
  }

  "assign a unique UUID" in {
    val request = SelectionRequest(1L)
    val selection1 = Selection(request, Seq(1L, 2L, 3L))
    val selection2 = Selection(request, Seq(1L, 2L, 3L))

    selection1.id must not(beEqualTo(selection2.id))
  }
}
