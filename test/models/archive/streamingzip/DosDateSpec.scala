package models.archive.streamingzip

import org.specs2.mutable.Specification
import java.util.Calendar
import java.util.Calendar._
import HexByteString._
import org.specs2.specification.Scope

class DosDateSpec extends Specification {
  
  "DosDate" should {
    
    "convert a calendar date to DOS date format" in new DosDateContext {
      
      val date = Calendar.getInstance()
      
      date.set(year, month, day, hour, minute, second)
      
      val dosDate = DosDate(date)

      dosDate.date must be equalTo dosDay 
      dosDate.time must be equalTo dosTime 
    }
  }

  "convert a DOS date to a calendar" in new DosDateContext {
    
    val date = DosDate.toCalendar(dosDay, dosTime)
    
    date.get(YEAR) must be equalTo year
    date.get(MONTH) must be equalTo month
    date.get(DAY_OF_MONTH) must be equalTo day
    
    date.get(HOUR_OF_DAY) must be equalTo hour
    date.get(MINUTE) must be equalTo minute
    date.get(SECOND) must be equalTo second
  }
  
  trait DosDateContext extends Scope {
    
    val (year, month, day, hour, minute, second) = (1995, 10, 19, 16, 6, 32)
    val dosDay = 0x1F53
    val dosTime = 0x80D0
  }
}