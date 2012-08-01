package controllers

import org.specs2.mutable.Specification
import java.util.IllegalFormatConversionException


class IdListSpec extends Specification {

  "IdList" should {
    
    "convert comma separated string to list of longs" in {
      val ids = List(23l, 4l, 66l, 8l)
      val idString = ids.mkString(", ")
      
      IdList(idString) must be equalTo ids
    }
    
    "throw exception if non-long argument found" in {
      IdList("23, 55, deadbeef") must throwAn[NumberFormatException]
    }
    
    "return empty list given an empty string" in {
      IdList("") must be empty
    }
  }
}