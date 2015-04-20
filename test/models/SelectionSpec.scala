package models

import org.specs2.mutable.Specification

class InMemorySelectionSpec extends Specification {
  "assign a unique UUID" in {
    val selection1 = InMemorySelection(Seq(1L, 2L, 3L))
    val selection2 = InMemorySelection(Seq(1L, 2L, 3L))

    selection1.id must not(beEqualTo(selection2.id))
  }
}
