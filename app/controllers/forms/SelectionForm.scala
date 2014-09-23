package controllers.forms

import play.api.data.{Form,Forms}
import models.{IdList,SelectionRequest}

object SelectionForm {
  def apply(documentSetId: Long) : Form[SelectionRequest] = {
    Form(
      Forms.mapping(
        "nodes" -> Forms.default(Forms.text, ""),
        "tags" -> Forms.default(Forms.text, ""),
        "documents" -> Forms.default(Forms.text, ""),
        "searchResults" -> Forms.default(Forms.text, "")
      )
      ((nodes, tags, documents, searchResults) =>
        SelectionRequest(documentSetId, nodes, tags, documents, searchResults)
      )
      ((selection: SelectionRequest) =>
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
