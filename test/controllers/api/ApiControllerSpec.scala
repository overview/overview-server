package controllers.api

import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json

class ApiControllerSpec extends Specification with JsonMatchers {
  "jsonError" should {
    "generate a JSON Error object" in {
      trait MyTest {
        self: ApiController =>

        val err = jsonError("foo")
      }

      val controller = new ApiController with MyTest

      Json.stringify(controller.err) must /("message" -> "foo")
    }
  }
}
