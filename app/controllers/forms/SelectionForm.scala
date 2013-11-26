package controllers.forms

import play.api.data.{Form,Forms}
import models.{IdList,Selection}

object SelectionForm {
  def apply(documentSetId: Long) : Form[Selection] = {
    Form(
      Forms.mapping(
        "nodes" -> Forms.default(Forms.text, ""),
        "tags" -> Forms.default(Forms.text, ""),
        "searchResults" -> Forms.default(Forms.text, ""),
        "documents" -> Forms.default(Forms.text, ""),
        "untagged" -> Forms.default(Forms.boolean, false)
      )
      ((nodes, tags, documents, searchResults, untagged) =>
        Selection(documentSetId, nodes, tags, documents, searchResults, untagged)
      )
      ((selection: Selection) =>
        Some((
          selection.nodeIds         .map(_.toString).mkString(","),
          selection.tagIds          .map(_.toString).mkString(","),
          selection.documentIds     .map(_.toString).mkString(","),
          selection.searchResultIds .map(_.toString).mkString(","),
          selection.untagged
        ))
      )
    )
  }
}
