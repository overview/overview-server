package models.orm.stores

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.stores.{ BaseStore, NoInsertOrUpdate }
import org.squeryl.Query

object DocumentStore extends BaseStore(models.orm.Schema.documents) with NoInsertOrUpdate[Document] {


}
