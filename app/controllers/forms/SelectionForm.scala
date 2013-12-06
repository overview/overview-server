package controllers.forms

import play.api.data.{Form,Forms}
import models.{IdList,Selection}

object SelectionForm {
  def apply(documentSetId: Long) : Form[Selection] = {
    Form(
      Forms.mapping(
        "nodes" -> Forms.default(Forms.text, ""),
        "tags" -> Forms.default(Forms.text, ""),
        "documents" -> Forms.default(Forms.text, ""),
        "searchResults" -> Forms.default(Forms.text, "")
      )
      ((nodes, tags, documents, searchResults) =>
        Selection(documentSetId, nodes, tags, documents, searchResults)
      )
      ((selection: Selection) =>
        Some((
          selection.nodeIds         .map(_.toString).mkString(","),
          selection.tagIds          .map(_.toString).mkString(","),
          selection.documentIds     .map(_.toString).mkString(","),
          selection.searchResultIds .map(_.toString).mkString(",")
        ))
      )
    )
  }
}
