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
    def index(treeId: Long, nodeids: String, tagids: String, documentids: String, start: Int, end: Int) = Action {
        val selection = paramsToSelection(treeId, nodeids, tagids, documentids)
        val documents = selection.findDocumentsSlice(start, end)
        val count = selection.findDocumentCount()
        Ok(JsObject(Seq(
            "documents" -> JsArray(
                documents.toSeq.map(document => JsObject(
                    Seq(
                        "id" -> JsNumber(JBigDecimal.valueOf(document.id)),
                        "title" -> JsString(document.title),
                        "tagids" -> JsArray(document.tags.toSeq.map(tag => JsNumber(JBigDecimal.valueOf(tag.id))))
                    )
                ))
            ),
            "total_items" -> JsNumber(JBigDecimal.valueOf(count))
        )))
    }

    private

    // TODO: move Selection construction to a static method in Selection
    // (I only didn't because Selection is Java and this is Scala)
    def idStringToIds(idsString: String) : Set[Long] = {
        val Long = """(\d{1,18})""".r
        val idStrings = idsString.split(',')
        val ret = new HashSet[Long]()

        for (idString <- idStrings) {
            val id = idString match {
                case Long(idString) => JLong.parseLong(idString)
                case _ => 0L
            }

            if (id != 0L) {
                ret.add(id)
            }
        }

        return ret
    }

    def paramsToSelection(treeId: Long, nodeidsString: String, tagidsString: String, documentidsString: String) : models.Selection = {
        val tree = models.Tree.find.byId(treeId)
        val nodeids = idStringToIds(nodeidsString)
        val tagids = idStringToIds(tagidsString)
        val documentids = idStringToIds(documentidsString)

        val selection = new models.Selection(tree, nodeids, tagids, documentids)

        return selection
    }
}
