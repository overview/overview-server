package org.overviewproject.models

import org.specs2.mutable.Specification

class ApiTokenSpec extends Specification {
  "ApiToken" should {
    "generate" should {
      "generate unique tokens" in {
        // We can't test they're cryptographically secure...
        val t1 = ApiToken.generate("user@example.org", 1L, "description")
        val t2 = ApiToken.generate("user@example.org", 1L, "description")

        t1.token must not be equalTo(t2.token)
      }

      "use the current time" in {
        val t = ApiToken.generate("", 1L, "")
        t.createdAt.getTime() must beCloseTo(System.currentTimeMillis(), 5000)
      }

      "set the createdBy" in {
        ApiToken.generate("user@example.org", 1L, "").createdBy must beEqualTo("user@example.org")
      }

      "set the documentSetId" in {
        ApiToken.generate("", 1L, "").documentSetId must beEqualTo(1L)
      }

      "set the description" in {
        ApiToken.generate("", 1L, "description").description must beEqualTo("description")
      }
    }
  }
}
