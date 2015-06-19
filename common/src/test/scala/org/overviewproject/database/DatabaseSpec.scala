package org.overviewproject.database

import org.specs2.mutable.Specification

class DatabaseSpec extends Specification {

  "Database" should {
    
    "be a singleton" in {
      val db1 = Database()
      val db2 = Database()

      db1 must be equalTo db2
    }
  }
}