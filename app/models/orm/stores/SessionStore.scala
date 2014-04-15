package models.orm.stores

import org.squeryl.KeyedEntityDef
import java.util.UUID

import models.orm.Session
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.postgres.SquerylEntrypoint._

object SessionStore extends BaseStore(models.orm.Schema.sessions)
