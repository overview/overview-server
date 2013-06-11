package org.overviewproject.postgres

import org.squeryl.PrimitiveTypeMode
import org.squeryl.dsl._
import org.squeryl.internals.StatementWriter
import org.squeryl.dsl.ast.{ExpressionNode,FunctionNode}

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

  class CastNode[A1,T1](arg: ExpressionNode, newType: String)(implicit tef: TypedExpressionFactory[A1,T1]) extends TypedExpression[A1,T1] {
    /** Writes (expression)::newType */
    override def doWrite(sw: StatementWriter) = {
      sw.write("(")
      arg.write(sw)
      sw.write(")::%s".format(newType))
    }

    override def children = List(arg)

    override def mapper = tef.createOutMapper
  }

  def cast(e: ExpressionNode, newType: String) = {
    newType.toLowerCase match {
      case "varchar" => new CastNode[String,TString](e, newType)
    }
  }

  def castAsInt(e: ExpressionNode) = new CastNode[Int,TInt](e, "int")

  /** Calls lo_unlink().
    *
    * You <em>must</em> select &amp;(lo_unlink(table.oidColumn)), and
    * <em>not</em> just lo_unlink(table.oidColumn). We don't know why.
    */
  def lo_unlink(e: TypedExpression[Option[Long],TOptionLong]
    )(implicit f: TypedExpressionFactory[Option[Int],TOptionInt]) = {

    f.convert(new FunctionNode("lo_unlink", Seq(e)))
  }
}
