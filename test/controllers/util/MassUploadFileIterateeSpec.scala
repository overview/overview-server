package controllers.util

import java.io.ByteArrayInputStream
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Random
import play.api.libs.iteratee.Enumerator
import org.overviewproject.tree.orm.{ FileGroup, GroupedFileUpload }
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope


class MassUploadFileIterateeSpec extends Specification with Mockito {

  "MassUploadFileIteratee" should {

    class TestMassUploadFileIteratee extends MassUploadFileIteratee {
      override val storage = smartMock[Storage]

      val fileUpload = smartMock[GroupedFileUpload] 
      val fileGroup = smartMock[FileGroup]
      fileGroup.id returns 1l

      storage.findCurrentFileGroup returns Some(fileGroup)
      storage.createUpload(any) returns fileUpload
      storage.appendData(any, any) returns fileUpload
    }

    trait UploadContext extends Scope {
      val data = new Array[Byte](100)
      Random.nextBytes(data)

      val iteratee = new TestMassUploadFileIteratee

      val input = new ByteArrayInputStream(data)
      val enumerator = Enumerator.fromStream(input)

      
      def result = {
        val resultFuture = enumerator.run(iteratee())
        Await.result(resultFuture, Duration.Inf)
      }
      
    }
    
    
    "produce a MassUploadFile" in new UploadContext {
      result must be equalTo(iteratee.fileUpload)
      there was one(iteratee.storage).findCurrentFileGroup
      there was one(iteratee.storage).appendData(iteratee.fileUpload, data)
    }
  }
}