package controllers

import java.sql.Connection
import play.api.mvc.{ AnyContent, Request }
import play.api.libs.json.JsValue
import org.squeryl.PrimitiveTypeMode._
import org.overviewproject.tree.orm.Node

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import models.{ OverviewUser, SubTreeLoader }
import models.orm.DocumentSet

object NodeController extends BaseController {
  def index(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    implicit val connection = models.OverviewDatabase.currentConnection
    val subTreeLoader = new SubTreeLoader(documentSetId)

    subTreeLoader.loadRootId match {
      case Some(rootId) => {
        val t0 = System.nanoTime
        val nodes = subTreeLoader.load(rootId, 3)
        val t1 = System.nanoTime
        val tags = subTreeLoader.loadTags(documentSetId)
        val t2 = System.nanoTime()
        val documents = subTreeLoader.loadDocuments(nodes, tags)
        val t3 = System.nanoTime()
        val json = views.json.Tree.show(nodes, documents, tags)
        val t4 = System.nanoTime()
        
        println("Times: %d %d %d %d".format(t1 - t0, t2 - t1, t3 - t2, t4 - t3))
        Ok(json)
      }
      case None => NotFound
    }
  }

  def show(documentSetId: Long, id: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    implicit val connection = models.OverviewDatabase.currentConnection
    val subTreeLoader = new SubTreeLoader(documentSetId)

    val nodes = subTreeLoader.load(id, 1)
    val documents = subTreeLoader.loadDocuments(nodes, Seq())

    val json = views.json.Tree.show(nodes, documents, Seq())
    Ok(json)
  }

  def update(documentSetId: Long, id: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
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
