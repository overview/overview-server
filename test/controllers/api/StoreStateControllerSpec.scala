package controllers.api

import play.api.libs.json.{JsObject,JsValue,Json}
import play.api.mvc.AnyContentAsJson
import scala.concurrent.Future

import controllers.backend.StoreBackend
import org.overviewproject.models.Store

class StoreStateControllerSpec extends ApiControllerSpecification {
  trait BaseScope extends ApiControllerScope {
    val mockBackend = mock[StoreBackend]
    val controller = new StoreStateController {
      override val backend = mockBackend
    }
  }

  "StoreStateController" should {
    "#show" should {
      trait ShowScope extends BaseScope {
        def store: Store = Store(123L, "api-tok3n", Json.obj("foo" -> "bar"))
        mockBackend.showOrCreate(any[String]) returns Future(store) // async
        override def action = controller.show()
      }

      "fetch using the request API token" in new ShowScope {
        status(result)
        there was one(mockBackend).showOrCreate(request.apiToken.token)
      }

      "return the JSON" in new ShowScope {
        status(result) must beEqualTo(OK)
        val json = contentAsString(result)

        json must /("foo" -> "bar")
      }
    }

    "#update" should {
      trait UpdateScope extends BaseScope {
        def store: Store = Store(123L, "api-tok3n", Json.obj("foo" -> "baz"))
        mockBackend.upsert(any[String], any[JsObject]) returns Future(store) // async

        val body: JsValue = Json.obj("foo" -> "baz")
        override lazy val result = controller.update()(fakeJsonRequest(body))
      }

      "return a 400 error on invalid input" in new UpdateScope {
        override val body = Json.arr("foo", "bar")
        status(result) must beEqualTo(BAD_REQUEST)
        contentAsString(result) must /("message" -> """You must POST a JSON Object.""")
      }

      "update the backend" in new UpdateScope {
        status(result)
        there was one(mockBackend).upsert(request.apiToken.token, Json.obj("foo" -> "baz"))
      }

      "return the updated object" in new UpdateScope {
        val json = contentAsString(result)
        json must /("foo" -> "baz")
      }
    }
  }
}
