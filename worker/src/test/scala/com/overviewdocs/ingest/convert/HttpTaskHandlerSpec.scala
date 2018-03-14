package com.overviewdocs.ingest.convert

import akka.http.scaladsl.model.{ContentTypes,HttpEntity,HttpHeader,Multipart,RequestEntity,StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.Specs2RouteTest
import akka.stream.scaladsl.{Keep,MergeHub,Sink,Source}
import akka.util.ByteString
import java.util.UUID
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.{After,Specification}
import org.specs2.specification.Scope
import play.api.libs.json.{JsObject,Json}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{Future,Promise,blocking}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.ingest.models.{BlobStorageRefWithSha1,ResumedFileGroupJob,WrittenFile2}
import com.overviewdocs.ingest.pipeline.StepOutputFragment
import com.overviewdocs.models.{BlobStorageRef,File2}
import com.overviewdocs.test.ActorSystemContext

class HttpTaskHandlerSpec extends Specification with Specs2RouteTest with Mockito with JsonMatchers {
  sequential

  // Specs2RouteTest conflicts with ActorSystemContext. But we can at least
  // share their confits.
  override def testConfigSource = ActorSystemContext.testConfigSource

  protected def await[T](future: Future[T]): T = {
    blocking(scala.concurrent.Await.result(future, scala.concurrent.duration.Duration("2s")))
  }

  trait BaseScope extends Scope with After {
    val (mergeSink, futureFragments) = MergeHub.source[StepOutputFragment](1) // buffer size 1: consume ASAP to avoid race
      .takeWhile(fragment => !fragment.isInstanceOf[StepOutputFragment.EndFragment], true)
      .toMat(Sink.collection[StepOutputFragment, Vector[_]])(Keep.both)
      .run

    val mockFileGroupJob = mock[ResumedFileGroupJob]
    mockFileGroupJob.isCanceled returns false
    val mockWrittenFile2 = WrittenFile2(
      id=1L,
      fileGroupJob=mockFileGroupJob,
      onProgress=(d => ???),
      rootId=None,
      parentId=None,
      filename="file.name",
      contentType="application/test",
      languageCode="fr",
      metadata=File2.Metadata(Json.obj("foo" -> "bar")),
      pipelineOptions=File2.PipelineOptions(true, false, Vector("Next")),
      blob=BlobStorageRefWithSha1(BlobStorageRef("loc:123", 20), ByteString("abcdabcdabcdabcdabcd").toArray)
    )

    val mockBlobStorage = mock[BlobStorage]
    mockBlobStorage.getUrlOpt(any[String], any[String]) returns Future.successful[Option[String]](None)

    def createTask: Task = Task(
      mockWrittenFile2,
      mergeSink
    )

    // Keep Source alive: HttpTaskHandler shuts down when it's completed
    val endPromise = Promise[Source[Task, akka.NotUsed]]()
    def end: Unit = {
      Source.single(StepOutputFragment.Canceled).runWith(mergeSink)
      endPromise.trySuccess(Source.empty[Task])
    }

    val nTasks: Int = 1
    lazy val tasks: Vector[Task] = Vector.fill[Task](nTasks)(createTask)
    val workerIdleTimeout: FiniteDuration = Duration(1, "s")
    val readTimeout: FiniteDuration = Duration(1, "s")
    val httpCreateTimeout: FiniteDuration = Duration(1, "s")

    lazy val server = new HttpTaskHandler(
      "Step",
      2,
      workerIdleTimeout,
      httpCreateTimeout
    )

    lazy val route: Route = {
      Source(tasks)
        .concat(Source.fromFutureSource(endPromise.future))
        .runWith(server.taskSink(mockBlobStorage))
    }

    def httpCreate = Post("/Step", "") ~> route
    def httpGet(uuid: String) = Get("/Step/" + uuid) ~> route
    def httpHead(uuid: String) = Head("/Step/" + uuid) ~> route
    def httpPost(uuid: String, subpath: String, entity: RequestEntity) = {
      Post("/Step/" + uuid + subpath, entity) ~> route ~> check { status must beEqualTo(StatusCodes.Accepted) }
    }

    def readTaskId(entity: HttpEntity): String = {
      Json.parse(await(responseEntity.toStrict(readTimeout)).data.toArray).as[JsObject].value("id").as[String]
    }

    def createWorkerTask: String = {
      httpCreate ~> check {
        status must beEqualTo(StatusCodes.Created)
        readTaskId(responseEntity)
      }
    }

    def sourceToBytes(source: Source[ByteString, _]): Array[Byte] = {
      await(source.runFold(ByteString.empty)(_ ++ _)).toArray
    }

    override def after = {
      end
      await(futureFragments)

    }
  }

  "HttpTaskHandlerSpec" should {
    "create a task" in new BaseScope {
      httpCreate ~> check {
        status must beEqualTo(StatusCodes.Created)
        val json = new String(await(responseEntity.toStrict(readTimeout)).data.toArray, "UTF-8")
        json must /("id" -> """[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""".r)
      }
    }

    "fail to create a second task if there is not one" in new BaseScope {
      override val httpCreateTimeout = Duration(1, "ms")
      createWorkerTask
      httpCreate ~> check { status must beEqualTo(StatusCodes.NoContent) }
    }

    "create a second task if there is one" in new BaseScope {
      override val httpCreateTimeout = Duration(10, "ms")
      override val nTasks = 2
      httpCreate ~> check { status must beEqualTo(StatusCodes.Created) }
      httpCreate ~> check { status must beEqualTo(StatusCodes.Created) }
      httpCreate ~> check { status must beEqualTo(StatusCodes.NoContent) }
    }

    "allow GET and HEAD of a task" in new BaseScope {
      val taskJson = httpCreate ~> check {
        status must beEqualTo(StatusCodes.Created)
        Json.parse(await(responseEntity.toStrict(readTimeout)).data.toArray).as[JsObject]
      }
      val taskId = taskJson.value("id").as[String]

      taskJson.value("filename").as[String] must beEqualTo("file.name")
      taskJson.value("contentType").as[String] must beEqualTo("application/test")
      taskJson.value("languageCode").as[String] must beEqualTo("fr")
      taskJson.value("metadata") must beEqualTo(Json.obj("foo" -> "bar"))
      taskJson.value("pipelineOptions") must beEqualTo(Json.obj("ocr" -> true, "splitByPage" -> false, "stepsRemaining" -> Json.arr("Next")))
      val blobJson = taskJson.value("blob").as[JsObject]
      blobJson.value("url").as[String] must beEqualTo("http://example.com/Step/" + taskId + "/blob")
      blobJson.value("nBytes").as[Int] must beEqualTo(20)
      blobJson.value("sha1").as[String] must beEqualTo("6162636461626364616263646162636461626364") // hex

      httpGet(taskId) ~> check {
        status must beEqualTo(StatusCodes.OK)
        Json.parse(await(responseEntity.toStrict(readTimeout)).data.toArray) must beEqualTo(taskJson)
      }
      httpHead(taskId) ~> check {
        status must beEqualTo(StatusCodes.OK)
      }
    }

    "allow GET of a task blob in S3" in new BaseScope {
      mockBlobStorage.getUrlOpt("loc:123", "application/test") returns Future.successful(Some("http://foo"))
      val taskJson = httpCreate ~> check {
        status must beEqualTo(StatusCodes.Created)
        Json.parse(await(responseEntity.toStrict(readTimeout)).data.toArray).as[JsObject]
      }
      val blobJson = taskJson.value("blob").as[JsObject]
      val blobUrl = blobJson.value("url").as[String]

      blobUrl must beEqualTo("http://foo")
    }

    "allow GET of a task blob in file storage" in new BaseScope {
      mockBlobStorage.getUrlOpt("loc:123", "application/test") returns Future.successful(None)
      mockBlobStorage.get("loc:123") returns Source.single(ByteString("here are the bytes"))

      val taskJson = httpCreate ~> check {
        status must beEqualTo(StatusCodes.Created)
        Json.parse(await(responseEntity.toStrict(readTimeout)).data.toArray).as[JsObject]
      }
      val blobJson = taskJson.value("blob").as[JsObject]
      val blobUrl = blobJson.value("url").as[String]

      Get(blobUrl) ~> route ~> check {
        status must beEqualTo(StatusCodes.OK)
        await(responseEntity.toStrict(readTimeout)).data must beEqualTo(ByteString("here are the bytes"))
      }
    }

    "allow to POST one fragment at a time" in new BaseScope {
      val taskId = createWorkerTask

      // There are races here: all the StepOutputFragments are being merged
      // together. But we _assume_ (in our _code_, not just tests!) that the
      // merging happens in order. That's usually true because we only send
      // an HTTP response once downstream has pulled all the HTTP request's
      // bytes. But there's no guarantee.
      //
      // Multipart POST is safer for users, since all entities arrive in the
      // same input bytestream. But we want to support these separate HTTP
      // requests because they make it so easy to write basic converters. In
      // practice, races are less likely than in our tests: a blob, for
      // instance, will only finish its POST once all its bytes have been read.

      httpPost(taskId, "/0.json", Json.toBytes(Json.obj(
        "filename" -> "aFilename",
        "contentType" -> "foo/bar",
        "languageCode" -> "fr",
        "metadata" -> Json.obj("foo" -> "bar"),
        "pipelineOptions" -> Json.obj("ocr" -> true, "splitByPage" -> false, "stepsRemaining" -> Json.arr())
      )))

      httpPost(taskId, "/0.blob", ByteString("blob"))
      httpPost(taskId, "/0.png", ByteString("thumb-png"))
      httpPost(taskId, "/0.jpg", ByteString("thumb-jpg"))
      httpPost(taskId, "/0.txt", ByteString("text"))
      httpPost(taskId, "/progress", Json.toBytes(Json.obj(
        "children" -> Json.obj("nProcessed" -> 1, "nTotal" -> 5)
      )))
      httpPost(taskId, "/progress", Json.toBytes(Json.obj(
        "bytes" -> Json.obj("nProcessed" -> 100, "nTotal" -> 10000)
      )))
      httpPost(taskId, "/progress", ByteString("23.1312"))
      httpPost(taskId, "/done", ByteString.empty)

      end

      val fragments = await(futureFragments).toIterator
      fragments.next must beEqualTo(StepOutputFragment.File2Header(
        0,
        "aFilename",
        "foo/bar",
        "fr",
        File2.Metadata(Json.obj("foo" -> "bar")),
        File2.PipelineOptions(true, false, Vector())
      ))
      fragments.next must beLike { case StepOutputFragment.Blob(i, b) =>
        i must beEqualTo(0)
        sourceToBytes(b) must beEqualTo("blob".getBytes("utf-8"))
      }
      fragments.next must beLike { case StepOutputFragment.Thumbnail(i, c, b) =>
        i must beEqualTo(0)
        c must beEqualTo("image/png")
        sourceToBytes(b) must beEqualTo("thumb-png".getBytes("utf-8"))
      }
      fragments.next must beLike { case StepOutputFragment.Thumbnail(i, c, b) =>
        i must beEqualTo(0)
        c must beEqualTo("image/jpeg")
        sourceToBytes(b) must beEqualTo("thumb-jpg".getBytes("utf-8"))
      }
      fragments.next must beLike { case StepOutputFragment.Text(i, b) =>
        i must beEqualTo(0)
        sourceToBytes(b) must beEqualTo("text".getBytes("utf-8"))
      }
      fragments.next must beEqualTo(StepOutputFragment.ProgressChildren(1, 5))
      fragments.next must beEqualTo(StepOutputFragment.ProgressBytes(100, 10000))
      fragments.next must beEqualTo(StepOutputFragment.ProgressFraction(23.1312))
      fragments.next must beEqualTo(StepOutputFragment.Done)
      fragments.hasNext must beFalse
    }

    "allow an error fragment" in new BaseScope {
      val taskId = createWorkerTask

      httpPost(taskId, "/error", ByteString("message"))

      end

      await(futureFragments) must beEqualTo(Vector(StepOutputFragment.FileError("message")))
    }

    "delete the task after Done" in new BaseScope {
      val taskId = createWorkerTask
      httpPost(taskId, "/done", Array[Byte]())
      Thread.sleep(5) // ICK -- slow down for race
      httpGet(taskId) ~> check { status must beEqualTo(StatusCodes.NotFound) }
    }

    "delete the task after Error" in new BaseScope {
      val taskId = createWorkerTask
      httpPost(taskId, "/error", ByteString("error"))
      Thread.sleep(5) // ICK -- slow down for race
      httpGet(taskId) ~> check { status must beEqualTo(StatusCodes.NotFound) }
    }

    "cancel the task before sending it to a worker" in new BaseScope {
      mockFileGroupJob.isCanceled returns true
      override val httpCreateTimeout = Duration(10, "ms")
      httpCreate ~> check { status must beEqualTo(StatusCodes.NoContent) }

      end

      await(futureFragments) must beEqualTo(Vector(StepOutputFragment.Canceled))
    }

    "cancel the task after sending it to a worker" in new BaseScope {
      override val httpCreateTimeout = Duration(10, "ms")
      mockFileGroupJob.isCanceled.returns(false).thenReturns(false).thenReturns(true)
      val taskId = createWorkerTask
      httpHead(taskId) ~> check { status must beEqualTo(StatusCodes.OK) }
      httpHead(taskId) ~> check { status must beEqualTo(StatusCodes.NotFound) }
      httpCreate ~> check { status must beEqualTo(StatusCodes.NoContent) }

      end

      await(futureFragments) must beEqualTo(Vector(StepOutputFragment.Canceled))
    }

    "time out a task" in new BaseScope {
      override val workerIdleTimeout = Duration(1, "microseconds")
      val taskId = createWorkerTask
      Thread.sleep(5) // ICKY.
      httpHead(taskId) -> check { status must beEqualTo(StatusCodes.NotFound) }
      val taskId2 = createWorkerTask
      taskId2 must not(beEqualTo(taskId))
    }

    "allow to POST all fragments at once with multipart/form-data" in new BaseScope {
      val taskId = createWorkerTask

      httpPost(taskId, "", Multipart.FormData(Source(Vector(
        Multipart.FormData.BodyPart.Strict(
          // Strict
          "0.json",
          HttpEntity.Strict(ContentTypes.`application/json`, ByteString(Json.toBytes(Json.obj(
            "filename" -> "aFilename",
            "contentType" -> "foo/bar",
            "languageCode" -> "fr",
            "metadata" -> Json.obj("foo" -> "bar"),
            "pipelineOptions" -> Json.obj("ocr" -> true, "splitByPage" -> false, "stepsRemaining" -> Json.arr())
          ))))
        ),
        Multipart.FormData.BodyPart(
          // indefinite-length; also, ignore the content-type
          "0.blob",
          HttpEntity.IndefiniteLength(ContentTypes.`application/octet-stream`, Source.single(ByteString("blob")))
        ),
        Multipart.FormData.BodyPart(
          // multiple chunks
          "0.png",
          HttpEntity.IndefiniteLength(ContentTypes.NoContentType, Source(Vector(
            ByteString("thumb"),
            ByteString("-png")
          )))
        ),
        Multipart.FormData.BodyPart(
          // ignore content-type; stream
          "0.jpg",
          HttpEntity.Default(ContentTypes.`application/octet-stream`, 9, Source.single(ByteString("thumb-jpg")))
        ),
        Multipart.FormData.BodyPart(
          "0.txt",
          HttpEntity.IndefiniteLength(ContentTypes.`text/plain(UTF-8)`, Source.single(ByteString("text")))
        ),
        Multipart.FormData.BodyPart(
          "progress",
          HttpEntity.IndefiniteLength(ContentTypes.NoContentType, Source.single(ByteString(Json.toBytes(
            Json.obj("children" -> Json.obj("nProcessed" -> 1, "nTotal" -> 5))
          ))))
        ),
        Multipart.FormData.BodyPart(
          "progress",
          HttpEntity.IndefiniteLength(ContentTypes.`application/json`, Source.single(ByteString(Json.toBytes(
            Json.obj("bytes" -> Json.obj("nProcessed" -> 100, "nTotal" -> 10000))
          ))))
        ),
        Multipart.FormData.BodyPart(
          "progress",
          HttpEntity.IndefiniteLength(ContentTypes.`application/json`, Source.single(ByteString("23.1312")))
        ),
        Multipart.FormData.BodyPart.Strict(
          "done",
          HttpEntity.Strict(ContentTypes.NoContentType, ByteString(""))
        )
      ))).toEntity)

      end

      val fragments = await(futureFragments).toIterator
      fragments.next must beEqualTo(StepOutputFragment.File2Header(
        0,
        "aFilename",
        "foo/bar",
        "fr",
        File2.Metadata(Json.obj("foo" -> "bar")),
        File2.PipelineOptions(true, false, Vector())
      ))
      fragments.next must beLike { case StepOutputFragment.Blob(i, b) =>
        i must beEqualTo(0)
        sourceToBytes(b) must beEqualTo("blob".getBytes("utf-8"))
      }
      fragments.next must beLike { case StepOutputFragment.Thumbnail(i, c, b) =>
        i must beEqualTo(0)
        c must beEqualTo("image/png")
        sourceToBytes(b) must beEqualTo("thumb-png".getBytes("utf-8"))
      }
      fragments.next must beLike { case StepOutputFragment.Thumbnail(i, c, b) =>
        i must beEqualTo(0)
        c must beEqualTo("image/jpeg")
        sourceToBytes(b) must beEqualTo("thumb-jpg".getBytes("utf-8"))
      }
      fragments.next must beLike { case StepOutputFragment.Text(i, b) =>
        i must beEqualTo(0)
        sourceToBytes(b) must beEqualTo("text".getBytes("utf-8"))
      }
      fragments.next must beEqualTo(StepOutputFragment.ProgressChildren(1, 5))
      fragments.next must beEqualTo(StepOutputFragment.ProgressBytes(100, 10000))
      fragments.next must beEqualTo(StepOutputFragment.ProgressFraction(23.1312))
      fragments.next must beEqualTo(StepOutputFragment.Done)
      fragments.hasNext must beFalse
    }
  }
}
