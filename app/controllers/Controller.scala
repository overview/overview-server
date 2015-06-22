package controllers

import java.util.UUID
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

  protected case class RequestData(request: Request[_]) {
    val data = requestData(request)

    /** Returns Some("foo") if query param key="foo"; returns Some("") if the
      * key is set but empty; returns None otherwise.
      */
    def getString(key: String): Option[String] = {
      data.get(key).flatMap(_.headOption)
    }

    /** Returns true if query param key="true"; returns false if the key is set
      * to anything else; returns None otherwise.
      */
    def getBoolean(key: String): Option[Boolean] = {
      data.get(key)
        .flatMap(_.headOption)
        .map(_ == "true")
    }

    /** Returns a UUID if query param key=[valid UUID]; returns None otherwise.
      */
    def getUUID(key: String): Option[UUID] = {
      data.get(key)
        .flatMap(_.headOption)
        .flatMap((s) => catching(classOf[IllegalArgumentException]).opt(UUID.fromString(s)))
    }

    /** Returns an Int if it exists and is valid; None otherwise.
      */
    def getInt(key: String): Option[Int] = {
      data.get(key)
        .flatMap(_.headOption)
        .flatMap((s) => catching(classOf[IllegalArgumentException]).opt(s.toInt))
    }

    /** Returns a Seq[Long] if key is set to something like "1,2,3"; returns
      * an empty Seq otherwise.
      */
    def getLongs(key: String): Seq[Long] = {
      val s = data.get(key).flatMap(_.headOption).getOrElse("")
      IdList.longs(s).ids
    }
  }

  protected def pageRequest(request: RequestHeader, maxLimit: Int) = {
    val requestOffset = queryStringParamToUnsignedInt(request, "offset")
    val requestLimit = queryStringParamToUnsignedInt(request, "limit")

    val offset = requestOffset.getOrElse(0)
    val limit = math.max(1, math.min(maxLimit, requestLimit.getOrElse(maxLimit)))
    PageRequest(offset, limit)
  }

  protected def jsonError(message: String) = views.json.api.error(message)
}
