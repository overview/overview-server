package models.orm.stores

import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.stores.{ BaseStore, NoInsertOrUpdate }

object DocumentStore extends BaseStore(models.orm.Schema.documents) with NoInsertOrUpdate[Document]
