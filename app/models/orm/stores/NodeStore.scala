package models.orm.stores

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.Node
import models.orm.Schema

object NodeStore extends BaseStore(models.orm.Schema.nodes) with NoInsertOrUpdate[Node]
