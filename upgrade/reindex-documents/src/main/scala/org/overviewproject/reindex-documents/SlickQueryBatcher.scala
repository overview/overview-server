package org.overviewproject.upgrade.reindex_documents

import scala.language.higherKinds
import slick.ast.Node
import slick.driver.JdbcProfile
import slick.lifted.{Query,RunnableCompiled}

/** Provides a "batch" method to call st.setFetchSize().
  *
  * See https://github.com/slick/slick/issues/809#issuecomment-49525364
  */
object SlickQueryBatcher {
  def createBatchInvoker[U](n: Node, param: Any, size: Int)(implicit driver: JdbcProfile): driver.QueryInvoker[U] =
    new driver.QueryInvoker[U](n, param) {
      override def setParam(st: java.sql.PreparedStatement): Unit = {
        super.setParam(st)
        st.setFetchSize(size)
}
      }

  def batch[U, C[_]](q: Query[_,U, C], size: Int)(implicit driver: JdbcProfile) =
    createBatchInvoker[U](driver.queryCompiler.run(q.toNode).tree, (), size)
  def batch[RU, C[_]](c: RunnableCompiled[_ <: Query[_, _, C], C[RU]], size: Int)(implicit driver: JdbcProfile) =
    createBatchInvoker[RU](c.compiledQuery, c.param, size)
}
