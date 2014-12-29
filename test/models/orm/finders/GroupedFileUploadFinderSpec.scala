package models.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.specs2.mutable.Specification
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication
import helpers.DbTestContext
import org.overviewproject.tree.orm.FileGroup
import org.overviewproject.tree.orm.FileJobState._
import org.overviewproject.tree.orm.GroupedFileUpload
import java.util.UUID

class GroupedFileUploadFinderSpec extends Specification {

  step(start(FakeApplication()))

  object Schema extends org.squeryl.Schema {
    override def columnNameFromPropertyName(propertyName: String) =
      NamingConventionTransforms.snakify(propertyName)

    override def tableNameFromClassName(className: String) =
      NamingConventionTransforms.snakify(className)

    val fileGroups = table[FileGroup]
    val groupedFileUploads = table[GroupedFileUpload]
  }

  "GroupedFileUploadFinder" should {

    trait UploadContext extends DbTestContext {
      val userEmail = "user@email.com"
      val guid = UUID.randomUUID()
      var fileGroup: FileGroup = _
      var upload: GroupedFileUpload = _

      override def setupWithDb = {
        fileGroup = Schema.fileGroups.insert(FileGroup(userEmail, InProgress))
        upload = Schema.groupedFileUploads.insert(GroupedFileUpload(
          fileGroup.id, guid, "content-type", "filename", 1000, 1000, 10l))
      }
    }

    "return None if GroupedFileUpload exists but not in the given FileGroup" in new UploadContext {
      GroupedFileUploadFinder.byFileGroupAndGuid(-1l, guid).headOption must beNone
    }

    "return None if GroupedFileUpload does not exist" in new UploadContext {
      val wrongGuid = UUID.randomUUID()
      GroupedFileUploadFinder.byFileGroupAndGuid(fileGroup.id, wrongGuid).headOption must beNone
    }

    "return GroupedFileUpload if it exists in the given FileGroup" in new UploadContext {
      GroupedFileUploadFinder.byFileGroupAndGuid(fileGroup.id, guid).headOption must beSome(upload)
    }
  }
  step(stop)
}