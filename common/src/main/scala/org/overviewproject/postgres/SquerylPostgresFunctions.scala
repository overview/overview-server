package org.overviewproject.postgres

import org.squeryl.PrimitiveTypeMode
import org.squeryl.dsl._
import org.squeryl.dsl.ast.FunctionNode

trait SquerylPostgresFunctions {
  self: PrimitiveTypeMode => 

  def string_agg[T2 >: TOptionString, T1 <: T2, A1, A2](
    e: TypedExpression[A1,T1],
    separator: TypedExpression[String,TString]
    )(implicit f: TypedExpressionFactory[A2,T2]) = {

    f.convert(new FunctionNode("string_agg", Seq(e, separator)))
  }

  def format[T2 >: TOptionString, T1 <: T2, A1, A2](
    format: TypedExpression[String,TString],
    e: TypedExpression[A1,T1]
    )(implicit f: TypedExpressionFactory[String,TString]) = {

    f.convert(new FunctionNode("format", Seq(format, e)))
  }
}
