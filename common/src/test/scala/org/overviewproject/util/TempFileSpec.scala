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

  class TempFileScope extends Scope {
    val tempFile = new TempFile

    def write(str: String) = {
      tempFile.outputStream.write(str.getBytes)
      tempFile.outputStream.flush
    }

    def readToEnd: String = {
      val ret = new Array[Byte](1024)
      val length = tempFile.inputStream.read(ret)
      new String(ret, 0, length)
    }
  }

  "TempFile" should {
    "write to and read from the file" in new TempFileScope {
      write("hello")
      readToEnd must beEqualTo("hello")
    }

    "read from the file after closing write" in new TempFileScope {
      write("hello")
      tempFile.outputStream.close
      readToEnd must beEqualTo("hello")
    }

    // Unfortunately, we can't test whether the file was deleted because the
    // interface doesn't return the filename because ... um ... the file was
    // deleted :).
  }
}
