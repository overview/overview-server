package controllers.forms

import play.api.data.{Form,Forms}

import org.overviewproject.tree.orm.Node

object NodeForm {
  def apply(node: Node) : Form[Node] = {
    Form(
      Forms.mapping(
        "description" -> Forms.nonEmptyText
      )
      ((description) => (node.copy(description=description)))
      ((node) => Some((node.description)))
    )
  }
}
