package controllers.forms

import play.api.data.Form
import play.api.data.Forms._

import org.overviewproject.jobs.models.Search

object SearchForm {
  def apply(documentSetId: Long) : Form[Search] = {
    Form(
      mapping(
        "query" -> nonEmptyText
      )
      ((query) => Search(documentSetId, query))
      (search => Some(search.query))
    )
  }
}
