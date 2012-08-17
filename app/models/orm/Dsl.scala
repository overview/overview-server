package models.orm

import org.squeryl.dsl.StringExpression
import org.squeryl.dsl.ast._
import org.squeryl.internals._

object Dsl {
  class Crypt(v1: StringExpression[String], v2: StringExpression[String], m: OutMapper[String])
    extends FunctionNode[String]("CRYPT", Some(m), Seq(v1, v2)) with StringExpression[String]

  class GenHash(v1: StringExpression[String], v2: StringExpression[String], m: OutMapper[String])
    extends FunctionNode[String]("GEN_HASH", Some(m), Seq(v1, v2)) with StringExpression[String]

  def crypt(v1: StringExpression[String], v2: StringExpression[String])(implicit m: OutMapper[String]) = new Crypt(v1, v2, m)

  def gen_hash(v1: StringExpression[String], v2: StringExpression[String])(implicit m: OutMapper[String]) = new GenHash(v1, v2, m)
}
