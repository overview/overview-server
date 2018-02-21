package com.overviewdocs.processing

import java.nio.file.Path
import org.specs2.mock.Mockito
import org.specs2.mutable.After
import play.api.libs.json.{JsObject,JsString}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.blobstorage.{BlobStorage,BlobBucketId}
import com.overviewdocs.database.LargeObject
import com.overviewdocs.models.{BlobStorageRef,FileGroup,GroupedFileUpload}
import com.overviewdocs.models.tables.{File2s,GroupedFileUploads}
import com.overviewdocs.test.DbSpecification

class GroupedFileUploadToFile2Spec extends DbSpecification with Mockito {

  trait BaseScope extends DbScope with After {
    val blobStorage = mock[BlobStorage]
    def stubBlobStorage = blobStorage.create(any[BlobBucketId], any[Path])

    val loManager = database.largeObjectManager
    // Large object contains text: "Hello, Large World!" (19 bytes)
    val oid = {
      import database.api._
      val action = (for {
        ret <- loManager.create
        lo <- loManager.open(ret, LargeObject.Mode.ReadWrite)
        _ <- lo.write("Hello, Large World!".getBytes("ascii"))
      } yield ret).transactionally
      blockingDatabase.run(action)
    }

    override def after = {
      import database.api._
      blockingDatabase.run(database.largeObjectManager.unlink(oid).transactionally)
    }

    val subject = new GroupedFileUploadToFile2(database, blobStorage)

    lazy val defaultFileGroup = factory.fileGroup()
    lazy val defaultGroupedFileUpload = factory.groupedFileUpload(fileGroupId=fileGroup.id, contentsOid=oid)

    def fileGroup: FileGroup = defaultFileGroup
    def groupedFileUpload: GroupedFileUpload = defaultGroupedFileUpload
    def run = await(subject.groupedFileUploadToFile2(fileGroup, groupedFileUpload))
    lazy val result = run

    def selectDbFile2s = blockingDatabase.seq(File2s)
    lazy val dbFile2s = selectDbFile2s

    def dbGroupedFileUpload(id: Long): Option[GroupedFileUpload] = {
      import database.api._
      blockingDatabase.option(GroupedFileUploads.filter(_.id === id))
    }
  }

  "GroupedFileUploadToFile2" should {
    "return a File2 with FileGroup metadata" in new BaseScope {
      val metadata = JsObject(Seq("foo" -> JsString("bar")))
      override val fileGroup = factory.fileGroup(metadataJson=metadata)
      override val groupedFileUpload = factory.groupedFileUpload(fileGroupId=fileGroup.id, contentsOid=oid, documentMetadataJson=None)
      stubBlobStorage returns Future.successful("loc")
      result.metadataJson must beEqualTo(metadata)
    }

    "return a File2 with GroupedFileUpload metadata, which overrides FileGroup metadata" in new BaseScope {
      val metadata1 = JsObject(Seq("foo" -> JsString("bar")))
      val metadata2 = JsObject(Seq("foo2" -> JsString("bar2")))

      override val fileGroup = factory.fileGroup(metadataJson=metadata1)
      override val groupedFileUpload = factory.groupedFileUpload(fileGroupId=fileGroup.id, contentsOid=oid, documentMetadataJson=Some(metadata2))
      stubBlobStorage returns Future.successful("loc")

      result.metadataJson must beEqualTo(metadata2)
    }

    "save a File2 to the database during create, before write" in new BaseScope {
      // write fails -- synchronously, even!
      stubBlobStorage throws new RuntimeException("error")
      try { run } catch { case _: Exception => {} }

      dbFile2s.length must beEqualTo(1)
      dbFile2s.head.blob must beNone
    }

    "write groupedFileUpload.file2Id, before write" in new BaseScope {
      // write fails -- synchronously, even!
      stubBlobStorage throws new RuntimeException("error")
      try { run } catch { case _: Exception => {} }

      dbGroupedFileUpload(groupedFileUpload.id).flatMap(_.file2Id) must beSome(dbFile2s.head.id)
    }

    "write to the File2, including sha1" in new BaseScope {
      stubBlobStorage returns Future.successful("loc")

      val sha1 = Array(
        0x3e, 0xeb, 0xf0, 0x05, 0xb3,
        0x18, 0xd9, 0x8a, 0x97, 0xfe,
        0x71, 0xcb, 0x75, 0xf2, 0x81,
        0x34, 0x5e, 0x8c, 0x6a, 0xa0
      ).map(_.toByte)

      result.blob must beSome(BlobStorageRef("loc", 19))
      result.blobSha1 must beEqualTo(sha1)
      result.writtenAt must beSome

      dbFile2s.head.blob must beSome(BlobStorageRef("loc", 19))
      dbFile2s.head.blobSha1 must beEqualTo(sha1)
      dbFile2s.head.writtenAt must beEqualTo(result.writtenAt)
    }

    "resume a write" in new BaseScope {
      stubBlobStorage
        .throws(new RuntimeException("error"))
        .thenReturns(Future.successful("loc"))
      try { run } catch { case _: Exception => {} }

      // The use case is: system goes offline before write finishes. We're
      // handling the case that groupedFileUpload.file2Id was written to the
      // database before we went offline.
      val file2Id = selectDbFile2s.map(_.id).head
      val groupedFileUploadForRestart = groupedFileUpload.copy(file2Id=Some(file2Id))
      await(subject.groupedFileUploadToFile2(fileGroup, groupedFileUploadForRestart))

      dbFile2s.length must beEqualTo(1)
      dbFile2s.head.blob must beSome(BlobStorageRef("loc", 19))
    }
  }
}
