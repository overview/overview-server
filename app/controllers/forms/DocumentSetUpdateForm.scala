package controllers.forms

import play.api.data._
import play.api.data.Forms._

import org.overviewproject.models.DocumentSet

object DocumentSetUpdateForm {

  def apply(documentSet: DocumentSet): Form[DocumentSet] = Form(
    mapping(
      "public" -> optional(boolean),
      "title" -> optional(text))
      ((public, title) => update(documentSet, public, title))
      (documentSet => Some((Some(documentSet.public), Some(documentSet.title)))))

  private def update(documentSet: DocumentSet, public: Option[Boolean], title: Option[String]): DocumentSet = {
    documentSet.copy(
      public=public.getOrElse(false),
      title=title.getOrElse(documentSet.title)
    )
  }
}
