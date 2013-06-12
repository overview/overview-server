package org.overviewproject.tree.orm.stores

trait NoInsertOrUpdate[A] {
  self: BaseStore[A] =>

  def insert(a: A): A = {
    table.insert(a)
  }

  def update(a: A): A = {
    table.update(a)
    a
  }

  override def insertOrUpdate(a: A): A = {
    throw new AssertionError("We can't insertOrUpdate here: use insert() or update() instead")
  }
}
