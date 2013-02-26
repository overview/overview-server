package org.overviewproject.postgres

import scala.collection.Seq
import org.specs2.mutable.Specification
import org.specs2.specification.Fragments
import org.specs2.main.ArgProperty

class PostgresqlEnumSpec extends Specification {

  class TestType1(v: String) extends PostgresqlEnum(v, "test_type1")
  class TestType2(v: String) extends PostgresqlEnum(v, "test_type2")

  object TestType1 {
    val Value1 = new TestType1("value1")
    val Value2 = new TestType1("value2")
  }

  object TestType2 {
    val T2Value1 = new TestType2("value1")
  }

  import TestType1._
  import TestType2._

  "PostgresqlEnum" should {

    "handle reference equality" in {
      Value1 == Value1 must beTrue
      Value2 == Value1 must beFalse
    }

    "set objects with the same typename and value to be equal" in {
      val generatedValue = new TestType1("value1")

      Value1 == generatedValue must beTrue
    }

    "set objects with different typenames or values to be not equal" in {
       Value1 == Value2 must beFalse
       Value1 == T2Value1 must beFalse
    }
    
    "set objects to be equal even if they're not the same class" in {
      val value = new PostgresqlEnum("value1", "test_type1")
      value == Value1 must beTrue
    }
    
    "handle hashcodes" in {
      val generatedValue = new TestType1("value1")
      Value1.## == generatedValue.## must beTrue
      Value1.## == Value2.## must beFalse
    }
  }
}