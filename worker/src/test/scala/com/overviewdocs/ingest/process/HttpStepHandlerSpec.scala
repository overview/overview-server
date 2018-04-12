package com.overviewdocs.ingest.process

import akka.http.scaladsl.model.{ContentTypes,HttpEntity,HttpHeader,Multipart,RequestEntity,StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.Specs2RouteTest
import akka.stream.scaladsl.{Keep,Sink,Source}
import akka.util.ByteString
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.{After,Specification}
import org.specs2.specification.Scope
import play.api.libs.json.{JsObject,Json}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{Future,Promise,blocking}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.ingest.model.{BlobStorageRefWithSha1,ResumedFileGroupJob,ConvertOutputElement,CreatedFile2,WrittenFile2,ProgressPiece}
import com.overviewdocs.models.BlobStorageRef
import com.overviewdocs.test.ActorSystemContext

// Kinda an integration test: tests WorkerTask and WorkerTaskPool as well as
// HttpStepHandler.route.
class HttpStepHandlerSpec extends Specification with Specs2RouteTest with Mockito with JsonMatchers {
  sequential

  // Specs2RouteTest conflicts with ActorSystemContext. But we can at least
  // share their confits.
  override def testConfigSource = ActorSystemContext.testConfigSource

  protected def await[T](future: Future[T]): T = {
    blocking(scala.concurrent.Await.result(future, scala.concurrent.duration.Duration("2s")))
  }

  trait BaseScope extends Scope with After {
    val mockFileGroupJob = mock[ResumedFileGroupJob]
    mockFileGroupJob.isCanceled returns false

    val postedFragments = ArrayBuffer.empty[StepOutputFragment]
    val mockStepOutputFragmentCollector = mock[StepOutputFragmentCollector]
    mockStepOutputFragmentCollector.initialStateForInput(any) returns StepOutputFragmentCollector.State.Start(
      StepOutputFragmentCollector.State.Parent(mock[WrittenFile2], ProgressPiece.Null, ProgressPiece.Null)
    )
    mockStepOutputFragmentCollector.transitionState(any, any)(any) answers { args =>
      val state = args.asInstanceOf[Array[Any]](0).asInstanceOf[StepOutputFragmentCollector.State]
      val end: StepOutputFragmentCollector.State = StepOutputFragmentCollector.State.End(Nil)
      val fragment = args.asInstanceOf[Array[Any]](1).asInstanceOf[StepOutputFragment]
      def drain(bytes: Source[ByteString, _]): Future[StepOutputFragmentCollector.State] = {
        bytes.runWith(Sink.ignore).map((_: akka.Done) => state)
      }
      postedFragments.+=(fragment)
      fragment match {
        case StepOutputFragment.Blob(_, bytes) => drain(bytes)
        case StepOutputFragment.Thumbnail(_, _, bytes) => drain(bytes)
        case StepOutputFragment.Text(_, bytes) => drain(bytes)
        case _: StepOutputFragment.EndFragment => Future.successful(end)
        case _ => Future.successful(state)
      }
    }

    def createTask: WrittenFile2 = {
      WrittenFile2(
        id=1L,
        fileGroupJob=mockFileGroupJob,
        progressPiece=ProgressPiece.Null,
        rootId=None,
        parentId=None,
        filename="file.name",
        contentType="application/test",
        languageCode="fr",
        metadata=Json.obj("foo" -> "bar"),
        wantOcr=true,
        wantSplitByPage=false,
        blob=BlobStorageRefWithSha1(BlobStorageRef("loc:123", 20), ByteString("abcdabcdabcdabcdabcd").toArray)
      )
    }

    val mockBlobStorage = mock[BlobStorage]
    mockBlobStorage.getUrlOpt(any[String], any[String]) returns Future.successful[Option[String]](None)

    // Keep Source alive: HttpStepHandler shuts down when it's completed
    val endPromise = Promise[Source[WrittenFile2, akka.NotUsed]]()
    def end: Unit = {
      endPromise.trySuccess(Source.empty[WrittenFile2])
      await(futureDone)
    }

    val nTasks: Int = 1
    lazy val tasks: Vector[WrittenFile2] = Vector.fill[WrittenFile2](nTasks)(createTask)
    val workerIdleTimeout: FiniteDuration = Duration(1, "s")
    val readTimeout: FiniteDuration = Duration(1, "s")
    val httpCreateTimeout: FiniteDuration = Duration(1, "s")

    lazy val server = new HttpStepHandler(
      "Step",
      mockBlobStorage,
      mockStepOutputFragmentCollector,
      2,
      workerIdleTimeout,
      httpCreateTimeout
    )

    lazy val (route: Route, futureDone: Future[akka.Done]) = {
      Source(tasks)
        .concat(Source.fromFutureSource(endPromise.future)) // don't complete until `end`
        .viaMat(server.flow(system))(Keep.right)
        .toMat(Sink.ignore)(Keep.both)
        .run()
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

    override def after = end
  }

  "HttpStepHandler" should {
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
      taskJson.value("wantOcr").as[Boolean] must beEqualTo(true)
      taskJson.value("wantSplitByPage").as[Boolean] must beEqualTo(false)
      val blobJson = taskJson.value("blob").as[JsObject]
      blobJson.value("url").as[String] must beEqualTo("http://example.com/Step/" + taskId + "/blob")
      blobJson.value("nBytes").as[Int] must beEqualTo(20)
      blobJson.value("sha1").as[String] must beEqualTo("6162636461626364616263646162636461626364") // hex

      Get(taskJson.value("url").as[String]) ~> route ~> check {
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

      httpPost(taskId, "/0.json", Json.toBytes(Json.obj(
        "filename" -> "aFilename",
        "contentType" -> "foo/bar",
        "languageCode" -> "fr",
        "metadata" -> Json.obj("foo" -> "bar"),
        "wantOcr" -> true,
        "wantSplitByPage" -> false
      )))

      httpPost(taskId, "/0.blob", ByteString("blob"))
      httpPost(taskId, "/0-thumbnail.png", ByteString("thumb-png"))
      httpPost(taskId, "/0-thumbnail.jpg", ByteString("thumb-jpg"))
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

      val fragments = postedFragments.toIterator
      fragments.next must beEqualTo(StepOutputFragment.File2Header(
        0,
        "aFilename",
        "foo/bar",
        "fr",
        Json.obj("foo" -> "bar"),
        true,
        false
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

      postedFragments.to[Vector] must beEqualTo(Vector(StepOutputFragment.FileError("message")))
    }

    "delete the task after Done" in new BaseScope {
      val taskId = createWorkerTask

      httpPost(taskId, "/0.json", Json.toBytes(Json.obj(
        "filename" -> "aFilename",
        "contentType" -> "foo/bar",
        "languageCode" -> "fr",
        "metadata" -> Json.obj("foo" -> "bar"),
        "wantOcr" -> true,
        "wantSplitByPage" -> false
      )))

      httpPost(taskId, "/0.blob", ByteString("blob"))
      httpPost(taskId, "/done", ByteString.empty)
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

      postedFragments.to[Vector] must beEqualTo(Vector(StepOutputFragment.Canceled))
    }.pendingUntilFixed // [adam, 2018-03-20] too lazy to implement this

    "cancel the task after sending it to a worker" in new BaseScope {
      override val httpCreateTimeout = Duration(10, "ms")
      val taskId = createWorkerTask
      mockFileGroupJob.isCanceled returns true
      httpHead(taskId) ~> check { status must beEqualTo(StatusCodes.NotFound) }
      httpCreate ~> check { status must beEqualTo(StatusCodes.NoContent) }

      end

      postedFragments.to[Vector] must beEqualTo(Vector(StepOutputFragment.Canceled))
    }

    //"time out a task" in new BaseScope {
    //  override val workerIdleTimeout = Duration(1, "microseconds")
    //  val taskId = createWorkerTask
    //  Thread.sleep(5) // ICKY.
    //  httpHead(taskId) -> check { status must beEqualTo(StatusCodes.NotFound) }
    //  val taskId2 = createWorkerTask
    //  taskId2 must not(beEqualTo(taskId))
    //}

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
            "wantOcr" -> true,
            "wantSplitByPage" -> false
          ))))
        ),
        Multipart.FormData.BodyPart.Strict(
          // heartbeat (no fragment)
          "heartbeat",
          HttpEntity.Strict(ContentTypes.NoContentType, ByteString(""))
        ),
        Multipart.FormData.BodyPart(
          // indefinite-length; also, ignore the content-type
          "0.blob",
          HttpEntity.IndefiniteLength(ContentTypes.`application/octet-stream`, Source.single(ByteString("blob")))
        ),
        Multipart.FormData.BodyPart(
          // multiple chunks
          "0-thumbnail.png",
          HttpEntity.IndefiniteLength(ContentTypes.NoContentType, Source(Vector(
            ByteString("thumb"),
            ByteString("-png")
          )))
        ),
        Multipart.FormData.BodyPart(
          // ignore content-type; stream
          "0-thumbnail.jpg",
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

      val fragments = postedFragments.toIterator
      fragments.next must beEqualTo(StepOutputFragment.File2Header(
        0,
        "aFilename",
        "foo/bar",
        "fr",
        Json.obj("foo" -> "bar"),
        true,
        false
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
