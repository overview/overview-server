package controllers

import play.api.db.DB
import play.api.mvc.{Action, Controller}
import play.api.data.{Form,FormError}
import play.api.Play.current

import models.{PersistentDocumentList,PersistentTag,PersistentTagLoader}

object TagController extends Controller {
  private val idListFormat = new play.api.data.format.Formatter[Seq[Long]] {
    def bind(key: String, data: Map[String,String]) : Either[Seq[FormError],Seq[Long]] = {
      val idList = data.get(key).map(s => IdList(s)).getOrElse(Seq())
      Right(idList)
    }

    def unbind(key: String, value: Seq[Long]) = Map(key -> value.mkString(","))
  }
  private val idList = play.api.data.Forms.of(idListFormat)

  def form(documentSetId: Long) = Form(
    play.api.data.Forms.mapping(
      "documents" -> idList,
      "nodes" -> idList,
      "tags" -> idList
    )
    ({ (documents, nodes, tags) =>
      // FIXME include documentSetId and tags in this constructor
      new PersistentDocumentList(nodes, tags, documents)
    })
    ( (documents: PersistentDocumentList) =>
      // FIXME should be: Some((documents.documentIds, documents.nodeIds, documents.tagIds))
      Some((Seq(), Seq(), Seq()))
    )
  )

  def add(documentSetId: Long, tagName: String) = Action { implicit request =>
    DB.withTransaction { implicit connection =>
      form(documentSetId).bindFromRequest.fold(
        formWithErrors => BadRequest,
        documents => {
          val tag = PersistentTag.findOrCreateByName(tagName, documentSetId)

          val tagUpdateCount = documents.addTag(tag.id)
          val tagTotalCount = tag.count

          Ok(views.json.Tag.add(tag.id, tagUpdateCount, tagTotalCount))
        }
      )
    }
  }
  
  def remove(documentSetId: Long, tagName: String) = Action { implicit request =>
    DB.withTransaction { implicit connection =>
      PersistentTag.findByName(tagName, documentSetId) match {
        case None => NotFound
        case Some(tag) => {
          form(documentSetId).bindFromRequest.fold(
            formWithErrors => BadRequest,
            documents => {
              val tagUpdateCount = documents.removeTag(tag.id)
              val tagTotalCount = tag.count

              Ok(views.json.Tag.remove(tag.id, tagUpdateCount, tagTotalCount))
            }
          )
        }
      }
    }
  }

  def nodeCounts(documentSetId: Long, tagName: String, nodeIds: String) = Action {
    DB.withTransaction { implicit connection =>

      PersistentTag.findByName(tagName, documentSetId) match {
        case Some(tag) => {
          val nodeCounts = tag.countsPerNode(IdList(nodeIds))

          Ok(views.json.Tag.nodeCounts(nodeCounts))
        }
        case None => NotFound
      }
    }
  }
}


