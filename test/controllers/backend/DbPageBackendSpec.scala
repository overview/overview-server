package controllers.backend

import org.overviewproject.models.Page

class DbPageBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new DbPageBackend with org.overviewproject.database.DatabaseProvider
  }

  "#show" should {
    trait ShowScope extends BaseScope {
      val file = factory.file()
      val page = factory.page(fileId=file.id)
    }

    "show a Page" in new ShowScope {
      val ret = await(backend.show(page.id))
      ret must beSome
      ret.map(_.id) must beSome(page.id)
      ret.map(_.dataLocation) must beSome(page.dataLocation)
      // etc
    }

    "not show a non-Page" in new ShowScope {
      await(backend.show(page.id + 1)) must beNone
    }
  }
}
