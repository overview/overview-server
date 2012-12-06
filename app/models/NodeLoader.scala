package models

import org.overviewproject.tree.orm.Node
import org.overviewproject.postgres.CustomTypes._

class NodeLoader {

  import models.orm.Schema._
  def loadRoot(documentSetId: Long): Option[Node] = {
    nodes.where(n => n.documentSetId === documentSetId and (n.parentId === Option.empty[Long])).headOption
  }
}