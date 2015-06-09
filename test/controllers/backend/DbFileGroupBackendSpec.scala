package controllers.backend

import org.overviewproject.models.FileGroup
import org.overviewproject.models.tables.FileGroups

class DbFileGroupBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new DbFileGroupBackend with org.overviewproject.database.DatabaseProvider

    def findFileGroups(userEmail: String, apiToken: Option[String], completed: Boolean): Seq[FileGroup] = {
      import databaseApi._
      blockingDatabase.seq(
        FileGroups
          .filter(_.userEmail === userEmail)
          .filter(g => (g.apiToken.isEmpty && apiToken.isEmpty) || (g.apiToken.isDefined && g.apiToken === apiToken))
          .filter(_.completed === completed)
      )
    }
  }

  "#findOrCreate" should {
    trait CreateScope extends BaseScope {
      val attributes = FileGroup.CreateAttributes("user@example.org", None)
      def findOrCreate: FileGroup = await(backend.findOrCreate(attributes))
    }

    "create a new file group" in new CreateScope {
      val fileGroup = findOrCreate
      fileGroup.userEmail must beEqualTo("user@example.org")
      fileGroup.apiToken must beNone
      fileGroup.completed must beFalse
      val dbFileGroup = findFileGroups("user@example.org", None, false).headOption
      dbFileGroup must beSome
    }

    "find an existing file group" in new CreateScope {
      factory.fileGroup(userEmail="user@example.org", apiToken=None, completed=false)
      val fileGroup = findOrCreate
      val dbFileGroups = findFileGroups("user@example.org", None, false)
      dbFileGroups.length must beEqualTo(1)
      dbFileGroups.head must beEqualTo(fileGroup)
    }

    "skip a deleted file group" in new CreateScope {
      val existing = factory.fileGroup(userEmail="user@example.org", apiToken=None, completed=false, deleted=true)
      findOrCreate.id must not(beEqualTo(existing.id))
    }

    "skip an existing file group when it is completed" in new CreateScope {
      val existing = factory.fileGroup(userEmail="user@example.org", apiToken=None, completed=true)
      findOrCreate.id must not(beEqualTo(existing.id))
    }

    "skip a FileGroup from a different user" in new CreateScope {
      val existing = factory.fileGroup(userEmail="user1@example.org", apiToken=None, completed=false)
      findOrCreate.id must not(beEqualTo(existing.id))
    }

    "skip a FileGroup with apiToken=null when called with non-null" in new CreateScope {
      val existing = factory.fileGroup(userEmail="user@example.org", apiToken=None, completed=false)
      override val attributes = FileGroup.CreateAttributes("user@example.org", Some("foo"))
      findOrCreate.id must not(beEqualTo(existing.id))
    }

    "skip a FileGroup with apiToken<>null when called with null" in new CreateScope {
      val existing = factory.fileGroup(userEmail="user@example.org", apiToken=Some("foo"), completed=false)
      findOrCreate.id must not(beEqualTo(existing.id))
    }

    "skip a FileGroup with a different apiToken" in new CreateScope {
      val existing = factory.fileGroup(userEmail="user@example.org", apiToken=Some("foo"), completed=false)
      override val attributes = FileGroup.CreateAttributes("user@example.org", Some("bar"))
      findOrCreate.id must not(beEqualTo(existing.id))
    }

    "find a FileGroup with the same apiToken" in new CreateScope {
      val existing = factory.fileGroup(userEmail="user@example.org", apiToken=Some("foo"), completed=false)
      override val attributes = FileGroup.CreateAttributes("user@example.org", Some("foo"))
      findOrCreate.id must beEqualTo(existing.id)
    }
  }

  "#find" should {
    trait FindScope extends BaseScope {
      def find(userEmail: String, apiToken: Option[String]): Option[FileGroup] = await(backend.find(userEmail, apiToken))
    }

    "find an existing file group" in new FindScope {
      val existing = factory.fileGroup(userEmail="user@example.org", apiToken=None, completed=false)
      find("user@example.org", None) must beSome(existing)
    }

    "skip a deleted file group" in new FindScope {
      factory.fileGroup(userEmail="user@example.org", apiToken=None, completed=false, deleted=true)
      find("user@example.org", None) must beNone
    }

    "skip an existing file group when it is completed" in new FindScope {
      factory.fileGroup(userEmail="user@example.org", apiToken=None, completed=true)
      find("user@example.org", None) must beNone
    }

    "skip a FileGroup from a different user" in new FindScope {
      val existing = factory.fileGroup(userEmail="user1@example.org", apiToken=None, completed=false)
      find("user@example.org", None) must beNone
    }

    "skip a FileGroup with apiToken=null when called with non-null" in new FindScope {
      factory.fileGroup(userEmail="user@example.org", apiToken=None, completed=false)
      find("user@example.org", Some("foo")) must beNone
    }

    "skip a FileGroup with apiToken<>null when called with null" in new FindScope {
      factory.fileGroup(userEmail="user@example.org", apiToken=Some("foo"), completed=false)
      find("user@example.org", None) must beNone
    }

    "skip a FileGroup with a different apiToken" in new FindScope {
      factory.fileGroup(userEmail="user@example.org", apiToken=Some("foo"), completed=false)
      find("user@example.org", Some("bar")) must beNone
    }

    "find a FileGroup with the same apiToken" in new FindScope {
      val existing = factory.fileGroup(userEmail="user@example.org", apiToken=Some("foo"), completed=false)
      find("user@example.org", Some("foo")) must beSome(existing)
    }
  }

  "#update" should {
    trait UpdateScope extends BaseScope {
      def update(id: Long, completed: Boolean): Unit = await(backend.update(id, completed))
    }

    "update a FileGroup" in new UpdateScope {
      val fileGroup = factory.fileGroup(userEmail="user@example.org", apiToken=None, completed=false)
      update(fileGroup.id, true)
      findFileGroups("user@example.org", None, true).headOption must beSome(fileGroup.copy(completed=true))
    }

    "skip a missing FileGroup" in new UpdateScope {
      val fileGroup = factory.fileGroup(userEmail="user@example.org", apiToken=None, completed=false)
      update(fileGroup.id + 1, true)
      findFileGroups("user@example.org", None, false).headOption must beSome(fileGroup)
    }

    "skip a deleted FileGroup" in new UpdateScope {
      val fileGroup = factory.fileGroup(userEmail="user@example.org", apiToken=None, completed=false, deleted=true)
      update(fileGroup.id, true)
      findFileGroups("user@example.org", None, false).headOption must beSome(fileGroup)
    }
  }

  "#destroy" should {
    trait DestroyScope extends BaseScope {
      def destroy(id: Long): Unit = await(backend.destroy(id))
    }

    "destroy a FileGroup" in new DestroyScope {
      val fileGroup = factory.fileGroup(userEmail="user@example.org", apiToken=None, completed=false, deleted=false)
      destroy(fileGroup.id)
      findFileGroups("user@example.org", None, false).headOption must beSome(fileGroup.copy(deleted=true))
    }

    "skip a missing FileGroup" in new DestroyScope {
      val fileGroup = factory.fileGroup(userEmail="user@example.org", apiToken=None, completed=false, deleted=false)
      destroy(fileGroup.id + 1)
      findFileGroups("user@example.org", None, false).headOption must beSome(fileGroup)
    }

    "skip a deleted FileGroup" in new DestroyScope {
      val fileGroup = factory.fileGroup(userEmail="user@example.org", apiToken=None, completed=false, deleted=true)
      destroy(fileGroup.id)
      findFileGroups("user@example.org", None, false).headOption must beSome(fileGroup)
    }
  }
}
