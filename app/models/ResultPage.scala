package models

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
  case class SquerylQueryResultPageSet[T](val query: Query[T], override val pageSize: Int) extends ResultPageSet[T] {
    override lazy val totalLength : Long = {
      import org.overviewproject.postgres.SquerylEntrypoint._

      from(query)(_ => compute(count()))
    }

    override def page(n: Int) = {
      import org.overviewproject.postgres.SquerylEntrypoint._ // for implicit queryToIterable

      val items = query.page(pageSize * (n - 1), pageSize).toIterable
      val details = ResultPageDetails(pageSize, n, totalLength)

      ResultPage(items, details)
    }
  }

  case class SeqResultPageSet[T](val seq: Seq[T], override val pageSize: Int) extends ResultPageSet[T] {
    override lazy val totalLength : Long = seq.length
    override def page(n: Int) = {
      val items = seq.slice((n - 1) * pageSize, n * pageSize)
      val details = ResultPageDetails(pageSize, n, totalLength)

      ResultPage(items, details)
    }
  }

  def apply[T](query: Query[T], pageSize: Int) = SquerylQueryResultPageSet(query, pageSize)
  def apply[T](seq: Seq[T], pageSize: Int) = SeqResultPageSet(seq, pageSize)
}

object ResultPage {
  def apply[T](query: Query[T], pageSize: Int, page: Int) : ResultPage[T] = ResultPageSet(query, pageSize).page(page)
  def apply[T](seq: Seq[T], pageSize: Int, page: Int) : ResultPage[T] = ResultPageSet(seq, pageSize).page(page)
}
