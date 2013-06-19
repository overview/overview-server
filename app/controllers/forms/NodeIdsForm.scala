package controllers.forms

import play.api.data.{Form,Forms}

import models.IdList

object NodeIdsForm {
  def apply() : Form[Seq[Long]] = {
    Form(
      Forms.mapping(
        "nodes" -> Forms.nonEmptyText
      )
      ((nodes) => IdList.longs(nodes).ids)
      ((ids) => Some(ids.map(_.toString).mkString(",")))
    )
  }
}
