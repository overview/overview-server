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

  protected def pageRequest(request: RequestHeader, maxLimit: Int) = {
    val requestOffset = queryStringParamToUnsignedInt(request, "offset")
    val requestLimit = queryStringParamToUnsignedInt(request, "limit")

    val offset = requestOffset.getOrElse(0)
    val limit = math.max(1, math.min(maxLimit, requestLimit.getOrElse(maxLimit)))
    PageRequest(offset, limit)
  }

  protected def selectionRequest(documentSetId: Long, request: Request[_]) = {
    val postData: Map[String, Seq[String]] = request.body match {
      case body: AnyContent if body.asFormUrlEncoded.isDefined => body.asFormUrlEncoded.get
      case _ => Map()
    }
    val getData: Map[String, Seq[String]] = request.queryString

    val requestData = getData ++ postData

    def requestString(key: String): String = {
      requestData
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
    val searchResultIds = requestIds("searchResults")
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
      searchResultIds,
      storeObjectIds,
      tagged,
      q
    )
  }
}

object Controller {
  private val MagicTagIdThatMeansUntagged = 0L
}
