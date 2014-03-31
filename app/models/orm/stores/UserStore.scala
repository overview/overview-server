package models.orm.stores

import org.overviewproject.tree.orm.stores.BaseStore

object UserStore extends BaseStore(models.orm.Schema.users) {
  def disableTreeTooltipsForEmail(email: String) : Unit = {
    import org.overviewproject.postgres.SquerylEntrypoint._

    update(table)(u =>
      where(u.email === email)
      set(u.treeTooltipsEnabled := false)
    )
  }
}
