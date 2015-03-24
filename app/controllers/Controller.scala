package controllers

import play.api.mvc.{AnyContent,Controller => PlayController,Request,RequestHeader}
import scala.util.control.Exception.catching

import models.pagination.PageRequest
import models.{IdList,SelectionRequest}

trait Controller extends PlayController {
  private def queryStringParamToUnsignedInt(request: RequestHeader, param: String): Option[Int] = {
    request.queryString
      .getOrElse(param, Seq())
      .headOption
      .flatMap((s) => catching(classOf[NumberFormatException]).opt(s.toInt))
      .filter((i) => i >= 0)
  }

  /** Returns a Map[String,String]: like requestData() but only the first
    * String.
    */
  protected def flatRequestData(request: Request[_]): Map[String,String] = {
    requestData(request)
      .mapValues(_.headOption)
      .collect { case (k, Some(v)) => (k -> v) }
  }

  /** Mimics Play's Form.bindFromRequest(). */
  protected def requestData(request: Request[_]): Map[String,Seq[String]] = {
    import play.api.mvc.{AnyContent,MultipartFormData}
    //import play.api.data.FormUtils
    // FormUtils is private[data]. Stupid.
    import play.api.libs.json._
    def fromJson(prefix: String = "", js: JsValue): Map[String, String] = js match {
      case JsObject(fields) => {
        fields.map { case (key, value) => fromJson(Option(prefix).filterNot(_.isEmpty).map(_ + ".").getOrElse("") + key, value) }.foldLeft(Map.empty[String, String])(_ ++ _) }
      case JsArray(values) => {
        values.zipWithIndex.map { case (value, i) => fromJson(prefix + "[" + i + "]", value) }.foldLeft(Map.empty[String, String])(_ ++ _) }
      case JsNull => Map.empty
      case JsUndefined() => Map.empty
      case JsBoolean(value) => Map(prefix -> value.toString)
      case JsNumber(value) => Map(prefix -> value.toString)
      case JsString(value) => Map(prefix -> value.toString)
    }

    val postData = request.body match {
      case body: AnyContent if body.asFormUrlEncoded.isDefined => body.asFormUrlEncoded.get
      case body: AnyContent if body.asMultipartFormData.isDefined => body.asMultipartFormData.get.asFormUrlEncoded
      case body: AnyContent if body.asJson.isDefined => fromJson(js = body.asJson.get).mapValues(Seq(_))
      case body: Map[_, _] => body.asInstanceOf[Map[String, Seq[String]]]
      case body: MultipartFormData[_] => body.asFormUrlEncoded
      case body: JsValue => fromJson(js = body).mapValues(Seq(_))
      case _ => Map.empty[String, Seq[String]]
    }

    request.queryString ++ postData
  }

  protected def pageRequest(request: RequestHeader, maxLimit: Int) = {
    val requestOffset = queryStringParamToUnsignedInt(request, "offset")
    val requestLimit = queryStringParamToUnsignedInt(request, "limit")

    val offset = requestOffset.getOrElse(0)
    val limit = math.max(1, math.min(maxLimit, requestLimit.getOrElse(maxLimit)))
    PageRequest(offset, limit)
  }

  protected def selectionRequest(documentSetId: Long, request: Request[_]) = {
    val reqData = requestData(request)

    def requestString(key: String): String = {
      reqData
        .getOrElse(key, Seq())
        .headOption
        .getOrElse("")
    }

    def requestIds(key: String): Seq[Long] = {
      IdList.longs(requestString(key)).ids
    }

    val nodeIds = requestIds("nodes")
    val tagIds = requestIds("tags")
    val documentIds = requestIds("documents")
    val storeObjectIds = requestIds("objects")
    val q = requestString("q")

    val deprecatedTagged = tagIds.indexOf(Controller.MagicTagIdThatMeansUntagged) match {
      case -1 => None
      case _ => Some(false)
    }
    val newTagged = requestString("tagged") match {
      case "true" => Some(true)
      case "false" => Some(false)
      case _ => None
    }
    val tagged = (newTagged ++ deprecatedTagged).headOption

    SelectionRequest(
      documentSetId,
      nodeIds,
      tagIds.filter(_ != Controller.MagicTagIdThatMeansUntagged),
      documentIds,
      storeObjectIds,
      tagged,
      q
    )
  }

  protected def jsonError(message: String) = views.json.api.error(message)
}

object Controller {
  private val MagicTagIdThatMeansUntagged = 0L
}
