package org.overviewproject.tree.orm.finders

import org.squeryl.Query

case class ResultPageDetails(
  val pageSize: Int,
  val pageNum: Int,
  val totalLength: Long
) {
  def isFirst : Boolean = pageNum == 1
  def isLast : Boolean = pageNum == lastPageNum
  def isOnlyPage : Boolean = isFirst && isLast
  def lastPageNum : Int = math.ceil(1.0 * totalLength / pageSize).toInt
}

case class ResultPage[A](
  val items: Iterable[A],
  val pageDetails: ResultPageDetails
) extends Iterable[A] {
  override def iterator = items.iterator
  def map[B](f: (A) => B) = ResultPage(items.map(f), pageDetails)
}

trait ResultPageSet[T] {
  val pageSize: Int

  def totalLength: Long
  def page(n: Int) : ResultPage[T]
}

object ResultPageSet {
  class SquerylQueryResultPageSetWithCountQuery[T1,T2](
    val query: Query[T1],
    val countQuery: Query[T2],
    override val pageSize: Int)
    extends ResultPageSet[T1] {

    override lazy val totalLength : Long = {
      import org.overviewproject.postgres.SquerylEntrypoint._

      from(countQuery)(_ => compute(count()))
    }

    override def page(n: Int) = {
      import org.overviewproject.postgres.SquerylEntrypoint._ // for implicit queryToIterable

      val items = query.page(pageSize * (n - 1), pageSize).toIterable
      val details = ResultPageDetails(pageSize, n, totalLength)

      ResultPage(items, details)
    }
  }

  class SquerylThreePhaseQueryResultPageSet[T1,T2](
    val simpleQuery: Query[T1],
    val fleshOutQuery: (Iterable[T1] => Query[T2]),
    val pageSize: Int)
    extends ResultPageSet[T2] {

    override lazy val totalLength : Long = {
      import org.overviewproject.postgres.SquerylEntrypoint._

      from(simpleQuery)(_ => compute(count()))
    }

    override def page(n: Int) = {
      import org.overviewproject.postgres.SquerylEntrypoint._ // for implicit queryToIterable

      val simpleItems = simpleQuery.page(pageSize * (n - 1), pageSize).toIterable
      val fleshedOutQuery = fleshOutQuery(simpleItems.toSeq) // Squeryl bug: it accepts an Iterable but expects a Seq.
      val fleshedOutItems = fleshedOutQuery.toIterable
      val details = ResultPageDetails(pageSize, n, totalLength)

      ResultPage(fleshedOutItems, details)
    }
  }

  class SeqResultPageSet[T](val seq: Seq[T], override val pageSize: Int) extends ResultPageSet[T] {
    override lazy val totalLength : Long = seq.length
    override def page(n: Int) = {
      val items = seq.slice((n - 1) * pageSize, n * pageSize)
      val details = ResultPageDetails(pageSize, n, totalLength)

      ResultPage(items, details)
    }
  }

  def apply[T1,T2](query: Query[T1], countQuery: Query[T2], pageSize: Int) = {
    new SquerylQueryResultPageSetWithCountQuery(query, countQuery, pageSize)
  }
  def apply[T1,T2](simpleQuery: Query[T1], fleshOutQuery: (Iterable[T1] => Query[T2]), pageSize: Int) = {
    new SquerylThreePhaseQueryResultPageSet(simpleQuery, fleshOutQuery, pageSize)
  }
  def apply[T](seq: Seq[T], pageSize: Int) = new SeqResultPageSet(seq, pageSize)
}

/** Constructors for building ResultPages.
  */
object ResultPage {
  /** ResultPage from a Query (or FinderResult).
    *
    * This will execute a count(*) using the query, then it will execute the
    * query for the desired page.
    */
  def apply[T](query: Query[T], pageSize: Int, page: Int) : ResultPage[T] = apply(query, query, pageSize, page)

  /** ResultPage from two Querys: one for full results and one to count.
    *
    * The ResultPage's count(*) will be applied to the second parameter. The
    * returned page will be populated by the first query.
    */
  def apply[T1,T2](query: Query[T1], countQuery: Query[T2], pageSize: Int, page: Int) : ResultPage[T1] = ResultPageSet(query, countQuery, pageSize).page(page)

  /** ResultPage from a Query for simple values and fleshed-out counterparts.
    *
    * The ResultPage's count(*) will be applied to the first query. Then
    * the first query will be run. Its output, as an Iterator[T1], will be
    * passed to fleshOutQuery(), which will create a new query for full values.
    * Those full values will be exported in the final ResultPage.
    *
    * val idQuery : Query[Long] = from(Schema.documents)(d =&gt; select(d.id))
    * val fleshOutQuery = { (docs: Iterable[Long]) =&gt;
    *   from(Schema.documents)(d =&gt;
    *     select(complex stuff...)
    *     where(d.id in docs)
    *   )
    * }
    * val resultPage = ResultPage(idQuery, fleshOutQuery, pageSize, page)
    *
    * There are three queries, total: 1. The count; 2. The simple query;
    * 3. The complex query, which uses the results from the simple query.
    *
    * Note that this is a hack. Squeryl does not support WITH queries, so we
    * need a database round-trip.
    *
    * Note: you MUST use the same ORDER BY in both queries.
    */
  def apply[T1,T2](simpleQuery: Query[T1], fullQuery: (Iterable[T1] => Query[T2]), pageSize: Int, page: Int) : ResultPage[T2] = {
    ResultPageSet(simpleQuery, fullQuery, pageSize).page(page)
  }

  /** ResultPage from a Seq. */
  def apply[T](seq: Seq[T], pageSize: Int, page: Int) : ResultPage[T] = ResultPageSet(seq, pageSize).page(page)
}
