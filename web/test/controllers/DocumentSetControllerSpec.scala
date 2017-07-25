package controllers

import org.mockito.Matchers
import org.specs2.matcher.{Expectable,JsonMatchers,Matcher}
import org.specs2.specification.Scope
import play.api.libs.json.{JsValue,Json}
import play.api.mvc.AnyContent
import scala.concurrent.Future

import com.overviewdocs.metadata.{MetadataField,MetadataFieldDisplay,MetadataFieldType,MetadataSchema}
import com.overviewdocs.models.{CsvImportJob,DocumentSet,ImportJob}
import com.overviewdocs.test.factories.PodoFactory
import controllers.auth.AuthorizedRequest
import controllers.backend.{DocumentSetBackend,ImportJobBackend,ViewBackend}
import controllers.util.JobQueueSender
import models.pagination.{Page,PageInfo,PageRequest}

class DocumentSetControllerSpec extends ControllerSpecification with JsonMatchers {
  trait BaseScope extends Scope {
    val factory = PodoFactory

    val IndexPageSize = 10
    val mockBackend = smartMock[DocumentSetBackend]
    val mockStorage = smartMock[DocumentSetController.Storage]
    val mockJobQueue = smartMock[JobQueueSender]
    val mockViewBackend = smartMock[ViewBackend]
    val mockImportJobBackend = smartMock[ImportJobBackend]

    val controller = new DocumentSetController(
      mockBackend,
      mockStorage,
      mockJobQueue,
      mockImportJobBackend,
      mockViewBackend,
      fakeControllerComponents,
      mockView[views.html.DocumentSet.index],
      mockView[views.html.DocumentSet.show],
      mockView[views.html.DocumentSet.showProgress]
    )
  }

  "DocumentSetController" should {
    "update" should {
      trait UpdateScope extends BaseScope {
        val documentSetId = 1L
        def formBody: Seq[(String, String)] = Seq("public" -> "false", "title" -> "foo")
        def request = fakeAuthorizedRequest.withFormUrlEncodedBody(formBody: _*)
        def result = controller.update(documentSetId)(request)

        mockBackend.show(documentSetId) returns Future.successful(Some(factory.documentSet(id=documentSetId)))
        mockBackend.updatePublic(any, any) returns Future.unit
      }

      "return 204" in new UpdateScope {
        h.status(result) must beEqualTo(h.NO_CONTENT)
      }

      "update the DocumentSet" in new UpdateScope {
        h.status(result) // invoke it
        there was one(mockBackend).updatePublic(1L, false)
      }

      "return NotFound if DocumentSet does not exist" in new UpdateScope {
        mockBackend.show(documentSetId) returns Future.successful(None)
        h.status(result) must beEqualTo(h.NOT_FOUND)
      }

      "return BadRequest if form input is bad" in new UpdateScope {
        override def formBody = Seq("public" -> "maybe", "title" -> "bar")
        h.status(result) must beEqualTo(h.BAD_REQUEST)
      }
    }

    "updateJson" should {
      trait UpdateJsonScope extends BaseScope {
        val documentSetId = 123L

        mockBackend.updateMetadataSchema(any, any) returns Future.unit

        def input: JsValue = Json.obj()
        lazy val request = fakeAuthorizedRequest.withJsonBody(input)
        lazy val result = controller.updateJson(documentSetId)(request)
      }

      "set a new Schema in the backend" in new UpdateJsonScope {
        override def input = Json.obj("metadataSchema" -> Json.obj(
            "version" -> 1,
            "fields" -> Json.arr(
              Json.obj("name" -> "foo", "type" -> "String", "display" -> "TextInput"),
              Json.obj("name" -> "bar", "type" -> "String", "display" -> "Div")
            )
          )
        )
        val expectSchema = MetadataSchema(1, Seq(
          MetadataField("foo", MetadataFieldType.String, MetadataFieldDisplay.TextInput),
          MetadataField("bar", MetadataFieldType.String, MetadataFieldDisplay.Div)
        ))
        h.status(result) must beEqualTo(h.NO_CONTENT)
        there was one(mockBackend).updateMetadataSchema(documentSetId, expectSchema)
      }

      "throw a 400 when the Schema is invalid JSON" in new UpdateJsonScope {
        override def input = Json.obj("metadataSchema" -> Json.arr("foo")) // completely wrong
        h.status(result) must beEqualTo(h.BAD_REQUEST)
        h.contentAsString(result) must /("code" -> "illegal-arguments")
        h.contentAsString(result) must contain("metadataSchema should look like")
        there was no(mockBackend).updateMetadataSchema(any, any)
      }

      "throw a 400 when the input is not JSON" in new UpdateJsonScope {
        override lazy val result = controller.updateJson(documentSetId)(fakeAuthorizedRequest)
        h.status(result) must beEqualTo(h.BAD_REQUEST)
        there was no(mockBackend).updateMetadataSchema(any, any)
      }

      "throw a 400 when the input does not contain a metadataSchema key" in new UpdateJsonScope {
        // when updateJson() allows both "name" and "metadataSchema", this will
        // test what happens when neither is specified
        override def input = Json.obj("metadataBlah" -> Json.arr("foo"))
        h.status(result) must beEqualTo(h.BAD_REQUEST)
        h.contentAsString(result) must /("code" -> "illegal-arguments")
        h.contentAsString(result) must contain("You must specify")
        there was no(mockBackend).updateMetadataSchema(any, any)
      }
    }

    "showHtmlInJson" should {
      trait ShowHtmlInJsonScope extends BaseScope {
        val documentSetId = 1L
        def result = controller.showHtmlInJson(documentSetId)(fakeAuthorizedRequest)
        mockBackend.show(documentSetId) returns Future.successful(Some(factory.documentSet(id=documentSetId)))
        mockStorage.findNViewsByDocumentSets(Seq(documentSetId)) returns Map(documentSetId -> 2)
      }

      "return NotFound if DocumentSet is not present" in new ShowHtmlInJsonScope {
        mockBackend.show(documentSetId) returns Future.successful(None)
        h.status(result) must beEqualTo(h.NOT_FOUND)
      }

      "return Ok if the DocumentSet exists but no trees do" in new ShowHtmlInJsonScope {
        mockStorage.findNViewsByDocumentSets(Seq(documentSetId)) returns Map()
        mockImportJobBackend.indexByDocumentSet(documentSetId) returns Future.successful(Seq())
        h.status(result) must beEqualTo(h.OK)
      }

      "return Ok if the DocumentSet exists with trees" in new ShowHtmlInJsonScope {
        mockImportJobBackend.indexByDocumentSet(documentSetId) returns Future.successful(Seq())
        h.status(result) must beEqualTo(h.OK)
      }
    }

    "showJson" should {
      trait ShowJsonScope extends BaseScope {
        val documentSetId = 1L
        def result = controller.showJson(documentSetId)(fakeAuthorizedRequest)

        val sampleTree = factory.tree(
          id = 1L,
          documentSetId = 1L,
          rootNodeId = Some(3L),
          title = "Tree title",
          documentCount = Some(10),
          lang = "en",
          suppliedStopWords = "",
          importantWords = ""
        )

        val sampleView = factory.view(title="a view")

        val sampleTag = factory.tag(id=1L, documentSetId=1L, name="a tag", color="ffffff")

        mockBackend.show(documentSetId) returns Future.successful(Some(factory.documentSet(documentCount=10)))
        mockStorage.findTrees(documentSetId) returns Seq(sampleTree)
        mockViewBackend.index(documentSetId) returns Future.successful(Seq(sampleView))
        mockStorage.findTags(documentSetId) returns Seq(sampleTag)
      }

      "encode the count, views, tags, and search results into the json result" in new ShowJsonScope {
        h.status(result) must beEqualTo(h.OK)

        val resultJson = h.contentAsString(result)
        resultJson must /("nDocuments" -> 10)
        resultJson must /("views") */("type" -> "view")
        resultJson must /("views") */("title" -> sampleTree.title)
        resultJson must /("views") */("title" -> "a view")
        resultJson must /("tags") */("name" -> "a tag")
      }
    }

    "show" should {
      trait ValidShowScope extends BaseScope {
        val documentSetId = 1L
        def request = fakeAuthorizedRequest
        lazy val result = controller.show(documentSetId)(request)
        mockBackend.show(documentSetId) returns Future.successful(Some(factory.documentSet(id=documentSetId)))
        mockImportJobBackend.indexByDocumentSet(documentSetId) returns Future.successful(Seq())
      }

      "return NotFound when the DocumentSet is not present" in new ValidShowScope {
        mockBackend.show(documentSetId) returns Future.successful(None)
        h.status(result) must beEqualTo(h.NOT_FOUND)
      }

      "return OK when okay" in new ValidShowScope {
        h.status(result) must beEqualTo(h.OK)
        h.contentType(result) must beSome("text/html")
      }

      "show a progressbar if there is an ImportJob" in new ValidShowScope {
        val job = smartMock[CsvImportJob]
        job.progress returns Some(0.123)
        job.description returns Some(("a-description", Seq()))
        job.estimatedCompletionTime returns Some(java.time.Instant.now)
        // Then the "cancel" button... icky test :(
        job.csvImport returns factory.csvImport(documentSetId=1L, id=2L)
        mockImportJobBackend.indexByDocumentSet(documentSetId) returns Future.successful(Seq(job))
        h.status(result)
        there was one(controller.showProgressHtml).apply(any, any, Matchers.eq(Seq(job)))(any, any, any)
      }
    }

    "index" should {
      trait IndexScope extends BaseScope {
        def pageNumber = 1

        def fakeDocumentSets: Seq[DocumentSet] = Seq(factory.documentSet(id=1L))
        mockBackend.indexPageByOwner(any, any) answers { _ => Future.successful(Page(fakeDocumentSets)) }
        def fakeNViews: Map[Long,Int] = Map(1L -> 2)
        mockStorage.findNViewsByDocumentSets(any[Seq[Long]]) answers { (_) => fakeNViews }
        def fakeJobs: Seq[ImportJob] = Seq()
        mockImportJobBackend.indexByUser(any[String]) answers { _ => Future.successful(fakeJobs) }

        def request = fakeAuthorizedRequest

        lazy val result = controller.index(pageNumber)(request)
      }

      "return Ok" in new IndexScope {
        h.status(result) must beEqualTo(h.OK)
      }

      "redirect to examples if there are no document sets" in new IndexScope {
        override def fakeDocumentSets = Seq()

        h.status(result) must beEqualTo(h.SEE_OTHER)
      }

      "flash when redirecting" in new IndexScope {
        override def fakeDocumentSets = Seq()

        override def request = super.request.withFlash("foo" -> "bar")
        h.flash(result).data must beEqualTo(Map("foo" -> "bar"))
      }

      "show page 1 if the page number is too low" in new IndexScope {
        override def pageNumber = 0
        h.status(result) must beEqualTo(h.OK) // load page
        there was one(mockBackend).indexPageByOwner(Matchers.eq(request.user.email), any)
      }

      "bind nViews to their document sets" in new IndexScope {
        override def fakeDocumentSets = Seq(factory.documentSet(id=1L), factory.documentSet(id=2L))
        override def fakeNViews = Map(1L -> 4, 2L -> 5)
        h.status(result) // load page

        case class haveIdPairs(idPairs: Seq[Tuple2[Long,Int]]) extends Matcher[Page[(DocumentSet,Iterable[ImportJob],Int)]] {
          def apply[P <: Page[(DocumentSet,Iterable[ImportJob],Int)]](p: Expectable[P]) = {
            result(
              p.value.items.map {
                case (documentSet, jobs, nViews) => (documentSet.id, nViews)
              } == idPairs,
              p.description + " has correct IDs",
              p.description + " has incorrect IDs",
              p
            )
          }
        }

        there was one(controller.indexHtml).apply(
          any,
          haveIdPairs(Seq((1, 4), (2, 5)))
        )(any, any, any)
      }
    }
  }
}
