package org.overviewproject.persistence.orm.stores

import org.overviewproject.tree.orm.stores.{ BaseStore, NoInsertOrUpdate }
import org.overviewproject.persistence.orm.Schema.trees
import org.overviewproject.tree.orm.Tree

object TreeStore extends BaseStore(trees) with NoInsertOrUpdate[Tree]