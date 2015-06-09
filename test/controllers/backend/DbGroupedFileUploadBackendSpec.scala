package controllers.backend

import java.util.UUID

import org.overviewproject.database.LargeObject
import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.models.tables.GroupedFileUploads

class DbGroupedFileUploadBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new DbGroupedFileUploadBackend with org.overviewproject.database.DatabaseProvider

    def findGroupedFileUpload(id: Long): Option[GroupedFileUpload] = {
      import blockingDatabaseApi._
      blockingDatabase.option(GroupedFileUploads.filter(_.id === id))
    }
  }

  "#index" should {
    trait IndexScope extends BaseScope {
      val fileGroup = factory.fileGroup()
      def index = await(backend.index(fileGroup.id))
    }

    "find an empty list" in new IndexScope {
      index must beEqualTo(Seq[GroupedFileUpload]())
    }

    "find GroupedFileUploads" in new IndexScope {
      val existing1 = factory.groupedFileUpload(fileGroupId=fileGroup.id)
      val existing2 = factory.groupedFileUpload(fileGroupId=fileGroup.id)
      index must containTheSameElementsAs(Seq(existing1, existing2))
    }

    "skip GroupedFileUploads in other FileGroups" in new IndexScope {
      val existing = factory.groupedFileUpload(fileGroupId=fileGroup.id)
      val otherFileGroup = factory.fileGroup()
      val other = factory.groupedFileUpload(fileGroupId=otherFileGroup.id)

      index must beEqualTo(Seq(existing))
    }
  }

  "#find" should {
    trait FindScope extends BaseScope {
      val fileGroup = factory.fileGroup()
      val guid = UUID.randomUUID
      def find(fileGroupId: Long, guid: UUID) = await(backend.find(fileGroupId, guid))
    }

    "find a GroupedFileUpload" in new FindScope {
      val existing = factory.groupedFileUpload(fileGroupId=fileGroup.id, guid=guid)
      find(fileGroup.id, guid) must beSome(existing)
    }

    "skip a GroupedFileUpload with a different fileGroupId" in new FindScope {
      val fileGroup2 = factory.fileGroup()
      factory.groupedFileUpload(fileGroupId=fileGroup2.id, guid=guid)
      find(fileGroup.id, guid) must beNone
    }

    "skip a GroupedFileUpload with a different guid" in new FindScope {
      factory.groupedFileUpload(fileGroupId=fileGroup.id, guid=UUID.randomUUID)
      find(fileGroup.id, guid) must beNone
    }
  }

  "#findOrCreate" should {
    trait FindOrCreateScope extends BaseScope {
      val fileGroup = factory.fileGroup()
      val guid = UUID.randomUUID

      val baseAttributes = GroupedFileUpload.CreateAttributes(
        fileGroup.id,
        guid,
        "application/octet-stream",
        "foo",
        123L
      )
      def findOrCreate(attributes: GroupedFileUpload.CreateAttributes): GroupedFileUpload = await(backend.findOrCreate(attributes))
    }

    "find a GroupedFileUpload" in new FindOrCreateScope {
      val existing = factory.groupedFileUpload(fileGroupId=fileGroup.id, guid=guid)
      findOrCreate(baseAttributes) must beEqualTo(existing)
    }

    "skip a GroupedFileUpload with a different fileGroupId" in new FindOrCreateScope {
      val fileGroup2 = factory.fileGroup()
      val existing = factory.groupedFileUpload(fileGroupId=fileGroup2.id, guid=guid)
      findOrCreate(baseAttributes) must not(beEqualTo(existing))
    }

    "skip a GroupedFileUpload with a different guid" in new FindOrCreateScope {
      val existing = factory.groupedFileUpload(fileGroupId=fileGroup.id, guid=UUID.randomUUID)
      findOrCreate(baseAttributes) must not(beEqualTo(existing))
    }

    "create with the proper properties" in new FindOrCreateScope {
      val returnValue = findOrCreate(baseAttributes)
      val dbValue = findGroupedFileUpload(returnValue.id)
      dbValue must beSome(returnValue)
      returnValue must beEqualTo(GroupedFileUpload(
        returnValue.id,
        fileGroup.id,
        guid,
        "application/octet-stream",
        "foo",
        123L,
        0L,
        returnValue.contentsOid
      ))
      returnValue.contentsOid must beGreaterThan(0L)
    }
  }

  "#writeBytes" should {
    trait WriteBytesScope extends BaseScope {
      val fileGroup = factory.fileGroup()
      // Use #findOrCreate(), because it gives us a new loid
      val groupedFileUpload = await(backend.findOrCreate(GroupedFileUpload.CreateAttributes(
        fileGroup.id, UUID.randomUUID, "application/octet-stream", "foo.abc", 123L
      )))

      def writeBytes(position: Long, bytes: Array[Byte]): Unit = {
        await(backend.writeBytes(groupedFileUpload.id, position, bytes))
      }

      def readBytes: Array[Byte] = {
        import databaseApi._
        blockingDatabase.run((for {
          lo <- blockingDatabase.largeObjectManager.open(groupedFileUpload.contentsOid, LargeObject.Mode.Read)
          bytes <- lo.read(9999)
        } yield bytes).transactionally)
      }
    }

    "write some bytes at position 0" in new WriteBytesScope {
      writeBytes(0L, "1234567890".getBytes("utf-8"))
      readBytes must beEqualTo("1234567890".getBytes("utf-8"))
    }

    "write some bytes at the last valid position" in new WriteBytesScope {
      writeBytes(0L, "1234567890".getBytes("utf-8"))
      writeBytes(10L, "1234567890".getBytes("utf-8"))
      readBytes must beEqualTo("12345678901234567890".getBytes("utf-8"))
    }

    "overwrite some bytes" in new WriteBytesScope {
      writeBytes(0L, "1234567890".getBytes("utf-8"))
      writeBytes(5L, "1234567890".getBytes("utf-8"))
      readBytes must beEqualTo("123451234567890".getBytes("utf-8"))
    }

    "update uploadedSize" in new WriteBytesScope {
      writeBytes(0L, "1234567890".getBytes("utf-8"))
      writeBytes(2L, "1234567890".getBytes("utf-8"))
      findGroupedFileUpload(groupedFileUpload.id) must beSome(groupedFileUpload.copy(uploadedSize=12))
    }
  }
}
