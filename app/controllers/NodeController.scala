package controllers

import java.sql.Connection
import play.api.mvc.{ AnyContent, Request }
import play.api.libs.json.JsValue
import org.squeryl.PrimitiveTypeMode._
import org.overviewproject.tree.orm.Node

import models.{ OverviewUser, SubTreeLoader }
import models.orm.DocumentSet

object NodeController extends BaseController {
  def index(documentSetId: Long) = authorizedAction(userOwningDocumentSet(documentSetId))(user => authorizedIndex(user, documentSetId)(_: Request[AnyContent], _: Connection))
  def show(documentSetId: Long, id: Long) = authorizedAction(userOwningDocumentSet(documentSetId))(user => authorizedShow(user, documentSetId, id)(_: Request[AnyContent], _: Connection))
  def update(documentSetId: Long, id: Long) = authorizedAction(userOwningDocumentSet(documentSetId))(user => authorizedUpdate(user, documentSetId, id)(_: Request[AnyContent], _: Connection))

  private[controllers] def authorizedIndex(user: OverviewUser, documentSetId: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    val subTreeLoader = new SubTreeLoader(documentSetId)

    subTreeLoader.loadRoot match {
      case Some(root) => {
        val nodes = subTreeLoader.load(root, 3)

        val tags = subTreeLoader.loadTags(documentSetId)
        val documents = subTreeLoader.loadDocuments(nodes, tags)

        val json = views.json.Tree.show(nodes, documents, tags)
        Ok(json)
      }
      case None => NotFound
    }
  }

  private[controllers] def authorizedShow(user: OverviewUser, documentSetId: Long, id: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    val subTreeLoader = new SubTreeLoader(documentSetId)

    subTreeLoader.loadNode(id) match {
      case Some(node) => {
        val nodes = subTreeLoader.load(node, 1)
        val documents = subTreeLoader.loadDocuments(nodes, Seq())

        val json = views.json.Tree.show(nodes, documents, Seq())
        Ok(json)
      }
      case None => NotFound
    }
  }

  private[controllers] def authorizedUpdate(user: OverviewUser, documentSetId: Long, id: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    val optionalNode = DocumentSet.findById(documentSetId)
      .flatMap(ds => ds.nodes.where(n => n.id === id).headOption)

    optionalNode match {
      case None => NotFound
      case Some(node) =>
        val form = forms.NodeForm(node)

        form.bindFromRequest().fold(
          f => BadRequest,
          node => {
            val savedNode = node.save()
            Ok(views.json.Node.show(savedNode))
          })
    }
  }
}
