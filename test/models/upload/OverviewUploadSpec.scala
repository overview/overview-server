package models.upload

import java.sql.Timestamp
import java.util.UUID
import org.postgresql.PGConnection

import com.overviewdocs.database.LargeObject
import com.overviewdocs.test.DbSpecification

class OverviewUploadSpec extends DbSpecification {
  "OverviewUpload" should {
    trait UploadContext extends DbScope {
      val guid = UUID.randomUUID
      val contentDisposition = "attachment; filename=file"
      val contentType = "text/csv"
      val totalSize = 42l
      val chunk: Array[Byte] = Array(0x12, 0x13, 0x14)
      var userId = 1L

      import database.api._

      blockingDatabase.runUnit(sqlu"""
        INSERT INTO "user" (id, email, role, password_hash, confirmed_at, email_subscriber, tree_tooltips_enabled)
        VALUES (1, 'admin@overview-project.org', 2, '', TIMESTAMP '1970-01-01 00:00:00', FALSE, FALSE);
      """)

      def withLargeObject[A](block: Long => A): A = {
        import database.api._

        val loid = blockingDatabase.run(database.largeObjectManager.create.transactionally)

        try {
          block(loid)
        } finally {
          blockingDatabase.run(database.largeObjectManager.unlink(loid).transactionally)
        }
      }
    }

    "create uploadedFile" in new UploadContext {
      withLargeObject { loid =>
        val before = new Timestamp(System.currentTimeMillis)
        val upload = OverviewUpload(userId, guid, contentDisposition, contentType, totalSize, loid)

        upload.lastActivity.compareTo(before) must beGreaterThanOrEqualTo(0)
        upload.size must be equalTo (totalSize)
        upload.contentsOid must be equalTo (loid) 
        upload.uploadedFile.contentDisposition must be equalTo (contentDisposition)
        upload.uploadedFile.contentType must be equalTo (contentType)
        upload.uploadedFile.size must be equalTo (0)
        upload.uploadedFile.uploadedAt.compareTo(before) must beGreaterThanOrEqualTo(0)
      }
    }

    "update bytesUploaded" in new UploadContext {
      withLargeObject { loid =>
        val before = new Timestamp(System.currentTimeMillis)
        val upload = OverviewUpload(userId, guid, contentDisposition, contentType, totalSize, loid)

        import database.api._
        implicit val ec = database.executionContext
        blockingDatabase.run((for {
          lo <- database.largeObjectManager.open(loid, LargeObject.Mode.Write)
          _ <- lo.write(chunk)
        } yield ()).transactionally)

        val updateTime = new Timestamp(System.currentTimeMillis)
        val updatedUpload = upload.withUploadedBytes(chunk.size)

        updatedUpload.lastActivity.compareTo(updateTime) must beGreaterThanOrEqualTo(0)
        updatedUpload.uploadedFile.size must be equalTo (chunk.size)
      }
    }

    "be saveable and findable by (userid, guid)" in new UploadContext {
      withLargeObject { loid =>
        val upload = OverviewUpload(userId, guid, contentDisposition, contentType, totalSize, loid)
        upload.contentsOid must be equalTo(loid)
        upload.save
      }

      val found = OverviewUpload.find(userId, guid)
      found must beSome
    }

    "leave valid uploadedFile when deleted" in new UploadContext {
      val uploadedFileId = withLargeObject { loid =>
        val upload = OverviewUpload(userId, guid, contentDisposition, contentType, totalSize, loid)
        upload.save
        upload.delete
        upload.uploadedFile.id
      }

      OverviewUpload.find(userId, guid) must beNone
      OverviewUploadedFile.findById(uploadedFileId) must beSome
    }

    "truncate large object" in new UploadContext {
      withLargeObject { loid =>
        val upload = OverviewUpload(userId, guid, contentDisposition, contentType, totalSize, loid).withUploadedBytes(234)
        val truncatedUpload = upload.truncate
        truncatedUpload.uploadedFile.size must be equalTo (0)
      }
    }

    "save changes in uploadedFile" in new UploadContext {
      val fileSize = 234

      withLargeObject { loid =>
        val upload = OverviewUpload(userId, guid, contentDisposition, contentType, totalSize, loid).withUploadedBytes(fileSize)
        upload.save
        val uploadedFile = OverviewUploadedFile.findById(upload.uploadedFile.id)
        uploadedFile must beSome.like { case u => u.size must be equalTo (fileSize) }
      }
    }

  }
}
