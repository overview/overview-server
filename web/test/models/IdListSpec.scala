package models

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class IdListSpec extends Specification {
  "IdList" should {
    "parse Longs" in {
      val ids = IdList.longs("1,2,3").ids
      ids must beEqualTo(Seq(1L, 2L, 3L))
    }

    "ignore non-integer params when parsing Longs" in {
      val ids = IdList.longs("some string that is most certainly not ID-ish").ids
      ids must beEqualTo(Seq[Long]())
    }

    "ignore too-long Longs" in {
      val ids = IdList.longs("18446744073709551616").ids // 2 ** 64
      ids must beEqualTo(Seq[Long]())
    }

    "allow valid Longs while disallowing too-long ones" in {
      val ids = IdList.longs("18446744073709551616,8446744073709551616").ids
      ids must beEqualTo(Seq(8446744073709551616L))
    }
  }
}
