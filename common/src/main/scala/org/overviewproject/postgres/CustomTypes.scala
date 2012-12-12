package org.overviewproject.postgres

import java.sql.{ Array => SqlArray }
import org.squeryl._
import org.squeryl.dsl._
import org.squeryl.adapters.PostgreSqlAdapter
import java.sql.Connection
import java.sql.ResultSet
import org.overviewproject.database.DB

sealed trait TPgArray
sealed trait TOptionPgArray

object CustomTypes extends PrimitiveTypeMode {
  private val ArrayBaseType = "bigint"
    
  implicit val pgArrayTEF = new TypedExpressionFactory[SqlArray, TPgArray] with PrimitiveJdbcMapper[SqlArray] {
    override def sample =
      if (Session.hasCurrentSession) zeroArray(Session.currentSession.connection)
      else DB.withConnection { zeroArray }
    
    val defaultColumnLength = 255
    def extractNativeJdbcValue(rs: ResultSet, i: Int) = rs.getArray(i)
    
    private def zeroArray(c: Connection): SqlArray = c.createArrayOf(ArrayBaseType, Array(0: java.lang.Long))
  }

  type LongArray = Array[Long]

  implicit val longArrayTEF = new NonPrimitiveJdbcMapper[SqlArray, LongArray, TPgArray](pgArrayTEF, this) {
    override def sample = Array[Long](0l)
    def convertFromJdbc(a: SqlArray) = {
      val objArray = a.getArray().asInstanceOf[Array[Object]]
      val longArray = objArray.map(_.asInstanceOf[Long])
      longArray
    }

    def convertToJdbc(la: Array[Long]) = Session.currentSession.connection.createArrayOf(ArrayBaseType, la.map(_.asInstanceOf[Object]))
  }

  implicit val optionLongArrayTEF =
    new TypedExpressionFactory[Option[LongArray], TOptionPgArray] with DeOptionizer[SqlArray, LongArray, TPgArray, Option[LongArray], TOptionPgArray] {

      val deOptionizer = longArrayTEF
    }

  implicit def longArrayToTE(la: LongArray) = longArrayTEF.create(la)
  implicit def optionJodaTimeToTE(s: Option[LongArray]) = optionLongArrayTEF.create(s)
}