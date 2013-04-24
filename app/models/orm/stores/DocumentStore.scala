package models.orm.stores

import org.overviewproject.tree.orm.Document

object DocumentStore extends BaseStore(models.orm.Schema.documents) with NoInsertOrUpdate[Document]
