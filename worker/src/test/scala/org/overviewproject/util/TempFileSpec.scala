/**
 * TempFileSpec.scala
 * 
 * Overview Project, created March 2013
 * @author Jonathan Stray
 * 
 */

import java.io.Reader
import org.overviewproject.util.TempFile
import org.specs2.mutable.Specification
import org.specs2.specification._

class TempFileSpec extends Specification {

  def readToEnd(r:Reader):String = {
    var str = ""
    var c = r.read()
    while (c != -1) {
      str += c.asInstanceOf[Char]
      c = r.read()
    } 
    str
  }
  
  val data1 = "Hello tempfile!\n"
  val data2 = "Nice knowing you."
  
  "TempFile" should {
    "write to and read from a file" in {  
      val tf = new TempFile
    
      tf.write(data1)
      
      val reader = tf.getReader   
      tf.flush()
      readToEnd(reader) must beEqualTo(data1)       // read what we've written so far

      tf.write(data2)
      tf.flush()
      readToEnd(reader) must beEqualTo(data2)       // read what we've written since last read
      
      tf.getReader should beEqualTo(reader)         // theere is only one reader object
    }
    
    "fail to read or write after close" in {
      val tf = new TempFile    
      tf.write(data1)

      tf.close()
      
      tf.write(data2) should throwA[java.io.IOException]      
      tf.getReader.read() should throwA[java.io.IOException]
    }
  }

}