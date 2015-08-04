package models.orm.stores

import com.overviewdocs.tree.orm.stores.BaseStore

object UserStore extends BaseStore(models.orm.Schema.users) {
  def disableTreeTooltipsForEmail(email: String) : Unit = {
    import com.overviewdocs.postgres.SquerylEntrypoint._

    update(table)(u =>
      where(u.email === email)
      set(u.treeTooltipsEnabled := false)
    )
  }
}
