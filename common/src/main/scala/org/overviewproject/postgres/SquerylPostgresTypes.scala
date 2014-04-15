package org.overviewproject.postgres

import scala.language.implicitConversions

import org.squeryl._
import org.squeryl.dsl._

trait SquerylPostgresTypes {
  self: PrimitiveTypeMode =>

  implicit val inetAddressTEF : NonPrimitiveJdbcMapper[String,InetAddress,TString] = new NonPrimitiveJdbcMapper[String, InetAddress, TString](stringTEF, this) {
    override def sample = InetAddress.getByName("127.0.0.1")
    override def convertFromJdbc(v: String) = InetAddress.getByName(v)
    override def convertToJdbc(v: InetAddress) = v.getHostAddress
  }

  implicit def inetAddressToTE(v: InetAddress) = inetAddressTEF.create(v)
}
