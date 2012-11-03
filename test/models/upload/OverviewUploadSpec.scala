package models.upload

import helpers.PgConnectionContext
import java.sql.Timestamp
import java.util.UUID._
import models.OverviewUser
import org.specs2.mutable.Specification
import play.api.Play.{start, stop}
import play.api.test.FakeApplication

class OverviewUploadSpec extends Specification {

  step(start(FakeApplication()))
  
  "OverviewUpload" should {

    trait UploadContext extends PgConnectionContext {
      val guid = randomUUID
      val filename = "file"
      val totalSize = 42l
      val chunk: Array[Byte] = Array(0x12, 0x13, 0x14)
      var userId = 1l
    }
    
    "be created with 0 size uploaded" in new UploadContext {
      LO.withLargeObject { lo =>
	val before = new Timestamp(System.currentTimeMillis)
	val upload = OverviewUpload(userId, guid, filename, totalSize, lo.oid)

	upload.lastActivity.compareTo(before) must beGreaterThanOrEqualTo(0)
	upload.bytesUploaded must be equalTo(0)
	upload.contentsOid must be equalTo(lo.oid)
      }
    }

    "update bytesUploaded" in new UploadContext {
      LO.withLargeObject { lo =>
	val before = new Timestamp(System.currentTimeMillis)
	val upload = OverviewUpload(userId, guid, filename, totalSize, lo.oid)

	val uploadedSize = lo.add(chunk)

	val updateTime = new Timestamp(System.currentTimeMillis)
	val updatedUpload = upload.withUploadedBytes(uploadedSize)
	
	updatedUpload.lastActivity.compareTo(updateTime) must beGreaterThanOrEqualTo(0)
	updatedUpload.bytesUploaded must be equalTo(uploadedSize)
      }
    }

    "be saveable and findable by (userid, guid)" in new UploadContext {
      LO.withLargeObject { lo =>
	val upload = OverviewUpload(userId, guid, filename, totalSize, lo.oid)
	upload.save
      }

      val found = OverviewUpload.find(userId, guid)
      found must beSome
    }


    "truncate large object" in new UploadContext {
      LO.withLargeObject { lo =>
	val upload = OverviewUpload(userId, guid, filename, totalSize, lo.oid).withUploadedBytes(234)
	val truncatedUpload = upload.truncate
	truncatedUpload.bytesUploaded must be equalTo(0)
      }
    }
    
  }

  step(stop)
}
