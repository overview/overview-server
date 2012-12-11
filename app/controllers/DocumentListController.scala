package controllers

import java.sql.Connection
import play.api.mvc.{AnyContent, Controller, Request}

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.util.{ IdList, SaneRange }
import models.{OverviewDatabase, PersistentDocumentList}

trait DocumentListController extends Controller {
  def index(documentSetId: Long, nodeids: String, tagids: String,
            documentids: String, start: Int, end: Int)
            = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>

    implicit val connection = OverviewDatabase.currentConnection

    val (validStart, validEnd) = SaneRange(start, end)

    val documents =
      new PersistentDocumentList(documentSetId,
        IdList(nodeids),
        IdList(tagids),
        IdList(documentids))
    val selection = documents.loadSlice(validStart, validEnd)
    val totalItems = documents.loadCount

    Ok(views.json.DocumentList.show(selection, totalItems))
  }
}

object DocumentListController extends DocumentListController
