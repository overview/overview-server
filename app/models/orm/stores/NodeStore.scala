package models.orm.stores

import org.overviewproject.postgres.SquerylEntrypoint._

import org.overviewproject.tree.orm.Node
import models.orm.Schema

object NodeStore {
  def save(node: Node): Node = Schema.nodes.insertOrUpdate(node)
}
