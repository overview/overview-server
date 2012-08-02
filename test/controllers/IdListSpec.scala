package controllers

import org.specs2.mutable.Specification
import java.util.IllegalFormatConversionException


class IdListSpec extends Specification {

  "IdList" should {
    
    "convert comma separated string to list of longs" in {
      val ids = List(23l, 4l, 66l, 8l)
      val idString = ids.mkString(",")
      
      IdList(idString) must be equalTo ids
    }
    
    "ignore non-numeric information" in {
      val ids = IdList("+++23, 55,deadbeef")
      
      ids must haveTheSameElementsAs(List(23, 55))
    }
    
    "return empty list given an empty string" in {
      IdList("") must be empty
    }
    
    "return empty list given no numbers in string" in {
      IdList("I am not a number, I am a free string") must be empty
    }
    
    "ignore numbers larger than largest Long" in {
      val tooBig = Long.MaxValue.toString + "0"
      
      IdList(tooBig + ",42") must contain(42l).only
    }
  }
}