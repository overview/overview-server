package views.json.helper

import org.overviewproject.test.DbSpecification
import models.PersistentTagInfo
import models.core._
import play.api.libs.json.Json.toJson
import org.overviewproject.tree.orm.DocumentProcessingError
import org.specs2.specification.Scope

class ModelJsonConvertersSpec extends DbSpecification {

  "JsonDocumentIdList" should {
    import views.json.helper.ModelJsonConverters.JsonDocumentIdList

    "write documentIdList attributes" in {
      val ids = List(10l, 20l, 34l)
      val count = 45l
      val documentIdList = DocumentIdList(ids, count)

      val documentIdListJson = toJson(documentIdList).toString

      documentIdListJson must contain("\"docids\":" + ids.mkString("[", ",", "]"))
      documentIdListJson must /("n" -> count)
    }
  }

  "JsonDocument" should {
    import views.json.helper.ModelJsonConverters.JsonDocument

    "write document attributes" in {
      val id = 39l
      val description = "description"
      val title = "title"
      val document = Document(id, description, Some(title), Some("unused by Tree"), Seq(1, 2, 3), Seq(22l, 11l, 33l))

      val documentJson = toJson(document).toString

      documentJson must /("id" -> id)
      documentJson must /("description" -> description)
      documentJson must /("title" -> title)
      documentJson must contain(""""tagids":[1,2,3]""")
      documentJson must contain(""""nodeids":[22,11,33]""")
    }
    
    "provide empty_title message if no title is provided" in {
    	val document = Document(1l, "description", None, None, Seq(1, 2, 3), Seq(22l, 11l, 33l))
    	
    	val documentJson = toJson(document).toString
    	
    	documentJson must /("title" -> "views.Document.show.title.empty_title")
    }
  }

  "JsonPersistentTag" should {
    import views.json.helper.ModelJsonConverters.JsonPersistentTagInfo
    import helpers.TestTag
    
    "write tag attributes" in {
      val documentCount = 5l
      val tag: PersistentTagInfo =
	TestTag(44l, "a tag", Some("e1e100"), DocumentIdList(Seq(10l), documentCount))

      val colorForJs = "#" + tag.color.get
      
      val tagJson = toJson(tag).toString
      tagJson must /("id" -> tag.id)
      tagJson must /("name" -> tag.name)
      tagJson must /("color" -> colorForJs)
      tagJson must /("doclist") */ ("n" -> documentCount)
    }

    "omit color value if color is not set" in {
      val noColor = None
      val tag: PersistentTagInfo = TestTag(0, "", noColor, DocumentIdList(Seq(10l), 4))

      val tagJson = toJson(tag).toString

      tagJson must not / ("color")
    }
  
  }
  
  "JsonDocumentProcessingError" should {
    import views.json.helper.ModelJsonConverters.JsonDocumentProcessingError 
    
    trait ErrorContext extends Scope {
      val url: String = "document url"
      def message: String
      def statusCode: Option[Int] = None
      def headers: Option[String] = None
      
      lazy val error: DocumentProcessingError = DocumentProcessingError(1, url, message, statusCode, headers)
      lazy val errorJson: String = toJson(error).toString
    }
    
    trait WorkerError extends ErrorContext {
      override def message = "exception thrown"
    }
    
    trait DocumentCloudServerError extends ErrorContext {
      val AccessDenied: Int = 403
      val NotFound: Int = 404
      val OtherError: Int = 500
      
      override def headers: Option[String] = Some("some headers")
    }
    
    trait AccessDeniedError extends DocumentCloudServerError {
      override def statusCode: Option[Int] = Some(AccessDenied)
      override def message = "DocumentCloud Access Denied message"
    }
    
    trait NotFoundError extends DocumentCloudServerError {
      override def statusCode: Option[Int] = Some(NotFound)
      override def message = "DocumentCloud Not Found message"
    }
    
    trait OtherServerError extends DocumentCloudServerError {
      override def statusCode: Option[Int] = Some(OtherError)
      override def message = "Other DocumentCloud Error"
    }
    
    "write url and default error message" in new WorkerError {
      errorJson must /("text_url" -> url)
      errorJson must /("message" -> "views.DocumentProcessingErrorList.show.message.internal_error") 
    }
    
    "write access denied error message on 403 error" in  new AccessDeniedError {
      errorJson must /("message" -> "views.DocumentProcessingErrorList.show.message.access_denied")
    }
    
    "write not found error message on 404 error" in new NotFoundError {
      errorJson must /("message" -> "views.DocumentProcessingErrorList.show.message.not_found")      
    }
    
    "write document cloud error on other errors" in new OtherServerError {
      errorJson must /("message" -> "views.DocumentProcessingErrorList.show.message.server_error")      
    }
  }
}
