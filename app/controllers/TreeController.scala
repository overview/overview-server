package controllers

import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities._
import models.orm.finders.{DocumentSetFinder, TreeFinder}
import org.overviewproject.tree.orm.{DocumentSet, Tree}

trait TreeController extends Controller {
  trait Storage {
    def findDocumentSet(id: Long) : Option[DocumentSet]
    def findTree(id: Long) : Option[Tree]

    /**
     * Returns true iff we can search the document set.
     *
     * This is a '''hack'''. All document sets ''should'' be searchable, but
     * document sets imported before indexing was implemented are not.
     */
    def isDocumentSetSearchable(documentSet: DocumentSet): Boolean
  }

  val storage : TreeController.Storage

  def show(documentSetId: Long, treeId: Long) = AuthorizedAction(userViewingDocumentSet(documentSetId)) { implicit request =>
    val stuff = for (tree <- storage.findTree(treeId).filter(_.documentSetId == documentSetId);
                     documentSet <- storage.findDocumentSet(documentSetId)) yield (tree, documentSet)

    stuff match {
      case None => NotFound
      case Some((tree, documentSet)) =>
        val isSearchable = storage.isDocumentSetSearchable(documentSet)
        Ok(views.html.Tree.show(request.user, documentSet, tree, isSearchable))
    }
  }
}

object TreeController extends TreeController {
  private val FirstSearchableDocumentSetVersion = 2

  object DatabaseStorage extends Storage {
    override def isDocumentSetSearchable(documentSet: DocumentSet) = documentSet.version >= FirstSearchableDocumentSetVersion
    override def findDocumentSet(id: Long) = DocumentSetFinder.byDocumentSet(id).headOption
    override def findTree(id: Long) = TreeFinder.byId(id).headOption
  }

  override val storage = DatabaseStorage
}
