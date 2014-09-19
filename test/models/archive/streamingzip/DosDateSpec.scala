package models.archive.streamingzip

import org.specs2.mutable.Specification
import java.util.Calendar
import HexByteString._

class DosDateSpec extends Specification {
  
  "DosDate" should {
    
    "convert a calendar date to DOS date format" in {
      
      val date = Calendar.getInstance()
      
      date.set(1995, 10, 19, 16, 6, 32)
      val dosDate = DosDate(date)

      dosDate.date must be equalTo 0x1F53 
      dosDate.time must be equalTo 0x80D0 
    }
  }

}