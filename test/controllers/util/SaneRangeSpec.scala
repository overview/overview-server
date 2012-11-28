package controllers.util

import org.specs2.mutable.Specification

class SaneRangeSpec extends Specification {
  
  "SaneRange" should {
    "return valid values unchanged" in {
      val goodStart = 6
      val goodEnd = 8
      
      val (start, end) = SaneRange(goodStart, goodEnd)
      
      (start, end) must be equalTo((goodStart, goodEnd))
    }
    
    "ensure start is not negative" in {
      val (start, _) = SaneRange(-4, 64)
      
      start must be equalTo(0)
    }
    
    "ensure end is greater than start" in {
      val start = 4
      
      val (_, end) = SaneRange(start, start - 2)
      val (_, notStart) = SaneRange(start, start)
      
      end must be equalTo(start + 1)
      notStart must be equalTo(start + 1)
    }
    
    "modify end after start is modified" in {
      val (_, end) = SaneRange(-5, -3)
      
      end must beEqualTo(1)
    }
  }
}