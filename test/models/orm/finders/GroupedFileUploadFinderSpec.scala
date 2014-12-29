package models.orm.finders

import java.util.UUID
import org.specs2.mutable.Specification
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication

import helpers.DbTestContext
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.GroupedFileUpload

class GroupedFileUploadFinderSpec extends Specification {

  step(start(FakeApplication()))

  object Schema extends org.squeryl.Schema {
    override def columnNameFromPropertyName(propertyName: String) =
      NamingConventionTransforms.snakify(propertyName)

    override def tableNameFromClassName(className: String) =
      NamingConventionTransforms.snakify(className)

    val groupedFileUploads = table[GroupedFileUpload]
  }

  "GroupedFileUploadFinder" should {

    trait UploadContext extends DbTestContext {
      val userEmail = "user@email.com"
      val guid = UUID.randomUUID()

      lazy val upload = {
        Schema.groupedFileUploads.insert(
          GroupedFileUpload(123, guid, "content-type", "filename", 1000, 1000, 10L)
        )
      }

      override def setupWithDb = {
        import scala.slick.jdbc.StaticQuery
        val session = new scala.slick.jdbc.UnmanagedSession(connection)
        StaticQuery.updateNA("""INSERT INTO file_group (id, user_email, completed, deleted) VALUES (123, 'user@example.org', FALSE, FALSE)""").execute(session)
        upload
        ()
      }
    }

    "return None if GroupedFileUpload exists but not in the given FileGroup" in new UploadContext {
      GroupedFileUploadFinder.byFileGroupAndGuid(124L, guid).headOption must beNone
    }

    "return None if GroupedFileUpload does not exist" in new UploadContext {
      val wrongGuid = UUID.randomUUID()
      GroupedFileUploadFinder.byFileGroupAndGuid(123L, wrongGuid).headOption must beNone
    }

    "return GroupedFileUpload if it exists in the given FileGroup" in new UploadContext {
      GroupedFileUploadFinder.byFileGroupAndGuid(123L, guid).headOption must beSome(upload)
    }
  }

  step(stop)
}
