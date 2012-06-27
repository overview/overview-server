package controllers

import collection.JavaConversions._
import collection.mutable._

import java.util.HashSet
import java.util.Set
import java.math.{BigDecimal => JBigDecimal}
import java.lang.{Long => JLong}

import play.api.data._
import play.api.mvc.{Action,Controller}
import play.api.libs.json._
import play.api.libs.json.Json._

import play.db.ebean.Model

object DocumentListController extends Controller {
    def index(treeId: Long, nodeids: String, tagids: String, documentids: String, start: Long, end: Long) = Action {
        val selection = paramsToSelection(treeId, nodeids, tagids, documentids)
        Ok(JsObject(Seq(
            "documents" -> JsArray(
                selection.slice(start, end).toSeq.map(document => JsObject(
                    Seq(
                        "id" -> JsNumber(JBigDecimal.valueOf(document.id)),
                        "title" -> toJson(document.title),
                        "tagids" -> JsArray(document.tags.toSeq.map(tag => JsNumber(JBigDecimal.valueOf(tag.id))))
                    )
                ))
            ),
            "total_items" -> JsNumber(JBigDecimal.valueOf(selection.count))
        )))
    }

    private

    // TODO: move Selection construction to a static method in Selection
    // (I only didn't because Selection is Java and this is Scala)
    def idStringToSet[T](finder: Model.Finder[JLong, T], idsString: String) : Set[T] = {
        val Long = "[0-9]{1,18}".r
        val idStrings = idsString.split(',')
        val ret = new HashSet[T]()

        for (idString <- idStrings) {
            val id = idString match {
                case Long(idString) => JLong.parseLong(idString)
                case _ => 0L
            }

            if (id != 0L) {
                val obj = finder.ref(id)
                if (obj != null) {
                    ret.add(obj)
                }
            }
        }

        return ret
    }

    def paramsToSelection(treeId: Long, nodeids: String, tagids: String, documentids: String) : models.Selection = {
        val tree = models.Tree.find.byId(treeId)
        val nodes = idStringToSet(models.Node.find, nodeids)
        val tags = idStringToSet(models.Tag.find, tagids)
        val documents = idStringToSet(models.Document.find, documentids)

        val selection = new models.Selection(tree)
//        selection.nodes.addAll(nodes)
//        selection.tags.addAll(tags)
//        selection.documents.addAll(documents)

        return selection
    }
}
