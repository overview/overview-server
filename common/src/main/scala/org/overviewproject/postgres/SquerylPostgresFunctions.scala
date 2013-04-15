package org.overviewproject.postgres

import org.squeryl.dsl.{ TypedExpression, TypedExpressionFactory, TOptionString, TString }
import org.squeryl.dsl.ast.FunctionNode

trait SquerylPostgresFunctions {
  def string_agg(
    e: TypedExpression[Option[String],TOptionString],
    separator: TypedExpression[String,TString]
    )(implicit f: TypedExpressionFactory[Option[String],TOptionString]) = {

    f.convert(new FunctionNode("string_agg", Seq(e, separator)))
  }
}
