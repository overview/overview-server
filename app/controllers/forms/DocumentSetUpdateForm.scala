package controllers.forms

import play.api.data._
import play.api.data.Forms._
import org.overviewproject.tree.orm.DocumentSet

object DocumentSetUpdateForm {

  def apply(documentSet: DocumentSet): Form[DocumentSet] = Form(
    mapping(
      "public" -> optional(boolean),
      "title" -> optional(text))
      ((isPublic, title) => update(documentSet, isPublic, title))
      (documentSet => Some((Some(documentSet.isPublic), Some(documentSet.title)))))

  private def update(documentSet: DocumentSet, isPublic: Option[Boolean], title: Option[String]): DocumentSet = {
    val isPublicUpdate = isPublic.getOrElse(documentSet.isPublic)
    val titleUpdate = title.getOrElse(documentSet.title)

    documentSet.copy(isPublic = isPublicUpdate, title = titleUpdate)
  }
}