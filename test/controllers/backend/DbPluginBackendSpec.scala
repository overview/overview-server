package controllers.backend

import java.util.UUID

import org.overviewproject.models.Plugin
import org.overviewproject.models.tables.Plugins

class DbPluginBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new DbPluginBackend with org.overviewproject.database.DatabaseProvider

    def findPlugin(id: UUID) = {
      import org.overviewproject.database.Slick.simple._
      Plugins.filter(_.id === id).firstOption(session)
    }
  }

  "DbPluginBackend" should {
    "#index" should {
      "sort plugins by name" in new BaseScope {
        val plugin1 = factory.plugin(id=new UUID(0L, 1L), name="zzz")
        val plugin2 = factory.plugin(id=new UUID(0L, 2L), name="aaa")

        val ret = await(backend.index)
        ret.length must beEqualTo(2)
        ret.map(_.id) must beEqualTo(Seq(plugin2.id, plugin1.id))
        ret.map(_.name) must beEqualTo(Seq("aaa", "zzz"))
      }
    }

    "#create" should {
      trait CreateScope extends BaseScope {
        val attributes = Plugin.CreateAttributes(
          name="name",
          description="description",
          url="https://example.org",
          autocreate=false,
          autocreateOrder=0
        )

        def create = await(backend.create(attributes))
        lazy val plugin = create
      }

      "return a Plugin" in new CreateScope {
        plugin.name must beEqualTo(attributes.name)
        plugin.description must beEqualTo(attributes.description)
        plugin.url must beEqualTo(attributes.url)
        plugin.autocreate must beEqualTo(attributes.autocreate)
        plugin.autocreateOrder must beEqualTo(attributes.autocreateOrder)
      }

      "write the Plugin to the database" in new CreateScope {
        val dbPlugin = findPlugin(plugin.id)
        dbPlugin.map(_.name) must beSome(attributes.name)
        dbPlugin.map(_.description) must beSome(attributes.description)
        dbPlugin.map(_.url) must beSome(attributes.url)
        dbPlugin.map(_.autocreate) must beSome(attributes.autocreate)
        dbPlugin.map(_.autocreateOrder) must beSome(attributes.autocreateOrder)
      }

      "pick a random ID" in new CreateScope {
        create.id must not(beEqualTo(create.id))
      }
    }

    "#update" should {
      trait UpdateScope extends BaseScope {
        val attributes = Plugin.UpdateAttributes(
          name="name",
          description="description",
          url="https://example.org",
          autocreate=true,
          autocreateOrder=1
        )

        val oldPlugin = factory.plugin(name="foo", description="bar", url="https://baz.org")
        val pluginId = oldPlugin.id

        def update = await(backend.update(pluginId, attributes))
        lazy val plugin = update
      }

      "return a Plugin" in new UpdateScope {
        plugin.map(_.name) must beSome(attributes.name)
        plugin.map(_.description) must beSome(attributes.description)
        plugin.map(_.url) must beSome(attributes.url)
        plugin.map(_.autocreate) must beSome(attributes.autocreate)
        plugin.map(_.autocreateOrder) must beSome(attributes.autocreateOrder)
      }

      "write the Plugin to the database" in new UpdateScope {
        update
        val dbPlugin = findPlugin(pluginId)
        dbPlugin.map(_.name) must beSome(attributes.name)
        dbPlugin.map(_.description) must beSome(attributes.description)
        dbPlugin.map(_.url) must beSome(attributes.url)
        dbPlugin.map(_.autocreate) must beSome(attributes.autocreate)
        dbPlugin.map(_.autocreateOrder) must beSome(attributes.autocreateOrder)
      }

      "return None when the Plugin is missing" in new UpdateScope {
        override val pluginId = UUID.randomUUID()
        update must beNone
      }
    }

    "#destroy" should {
      "destroy a Plugin" in new BaseScope {
        val plugin = factory.plugin()
        await(backend.destroy(plugin.id))
        findPlugin(plugin.id) must beNone
      }

      "leave other Plugins in the database" in new BaseScope {
        val plugin1 = factory.plugin()
        val plugin2 = factory.plugin()
        await(backend.destroy(plugin2.id))
        findPlugin(plugin1.id) must beSome
      }

      "work even when the Plugin did not exist" in new BaseScope {
        { await(backend.destroy(UUID.randomUUID())) } must not(throwA[Exception])
      }
    }
  }
}
