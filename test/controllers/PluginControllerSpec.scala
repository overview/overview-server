package controllers

import java.util.UUID
import org.specs2.matcher.JsonMatchers
import org.specs2.specification.Scope
import play.api.libs.json.Json
import scala.concurrent.Future

import controllers.backend.PluginBackend
import org.overviewproject.models.Plugin
import org.overviewproject.test.factories.PodoFactory

class PluginControllerSpec extends ControllerSpecification with JsonMatchers {
  trait BaseScope extends Scope {
    val mockBackend = mock[PluginBackend]
    val controller = new PluginController {
      override val backend = mockBackend
    }
    val factory = PodoFactory
  }

  "#index" should {
    trait IndexScope extends BaseScope

    "return some JSON" in new IndexScope {
      val plugin = factory.plugin(name="n", description="d", url="http://u.org")
      mockBackend.index returns Future.successful(Seq(plugin))
      val result = controller.index(fakeAuthorizedRequest)
      factory.plugin(name="foo")

      h.status(result) must beEqualTo(h.OK)
      h.contentType(result) must beSome("application/json")
      val json = h.contentAsString(result)

      json must /#(0) /("name" -> "n")
      json must /#(0) /("description" -> "d")
      json must /#(0) /("url" -> "http://u.org")
      json must /#(0) /("id" -> plugin.id.toString())
    }
  }

  "#create" should {
    trait CreateScope extends BaseScope {
      val request = fakeAuthorizedRequest.withFormUrlEncodedBody(
        "name" -> "foo",
        "description" -> "bar",
        "url" -> "http://baz.org"
      )
      lazy val result = controller.create(request)

      val plugin = factory.plugin(name="n", description="d", url="http://u.org")
      mockBackend.create(any) returns Future.successful(plugin)
    }

    "create a Plugin in the database" in new CreateScope {
      result

      there was one(mockBackend).create(Plugin.CreateAttributes(
        name="foo",
        description="bar",
        url="http://baz.org"
      ))
    }

    "return the created Plugin" in new CreateScope {
      val json = h.contentAsString(result)

      json must /("id" -> plugin.id.toString())
      json must /("name" -> "n")
      json must /("description" -> "d")
      json must /("url" -> "http://u.org")
    }

    "return BadRequest for an invalid request" in new CreateScope {
      override val request = fakeAuthorizedRequest.withFormUrlEncodedBody("foo" -> "bar")
      h.status(result) must beEqualTo(h.BAD_REQUEST)
    }
  }

  "#update" should {
    trait UpdateScope extends BaseScope {
      val plugin = factory.plugin(name="n", description="d", url="http://u.org")
      val plugin2 = factory.plugin(name="n2", description="d2", url="http://u2.org")

      val pluginId = plugin.id
      val request = fakeAuthorizedRequest.withFormUrlEncodedBody(
        "name" -> "foo",
        "description" -> "bar",
        "url" -> "http://baz.org"
      )
      lazy val result = controller.update(pluginId)(request)
    }

    "update a Plugin in the database" in new UpdateScope {
      mockBackend.update(any, any) returns Future.successful(Some(plugin2))
      result
      there was one(mockBackend).update(pluginId, Plugin.UpdateAttributes(
        name="foo",
        description="bar",
        url="http://baz.org"
      ))
    }

    "returns the updated Plugin" in new UpdateScope {
      mockBackend.update(any, any) returns Future.successful(Some(plugin2))

      val json = h.contentAsString(result)

      json must /("id" -> plugin2.id.toString())
      json must /("name" -> "n2")
      json must /("description" -> "d2")
      json must /("url" -> "http://u2.org")
    }

    "returns 404 Not Found on wrong ID" in new UpdateScope {
      mockBackend.update(any, any) returns Future.successful(None)
      h.status(result) must beEqualTo(h.NOT_FOUND)
    }

    "returns 400 Bad Request on invalid form" in new UpdateScope {
      override val request = fakeAuthorizedRequest.withFormUrlEncodedBody("foo" -> "bar")
      h.status(result) must beEqualTo(h.BAD_REQUEST)
    }
  }

  "#destroy" should {
    trait DestroyScope extends BaseScope {
      mockBackend.destroy(any) returns Future.successful(())
      val pluginId = new UUID(1L, 2L)
      val result = controller.destroy(pluginId)(fakeAuthorizedRequest)
    }

    "return 200 Ok" in new DestroyScope {
      h.status(result) must beEqualTo(h.OK)
    }

    "delete the Plugin from the database" in new DestroyScope {
      there was one(mockBackend).destroy(pluginId)
    }
  }
}
