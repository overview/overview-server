package persistence

import java.io.InputStream
import org.overviewproject.database.Database
import org.overviewproject.postgres.LO
import org.overviewproject.database.DB

class LargeObjectInputStream(oid: Long, bufferSize: Int = 8012) extends InputStream {

  private val buffer = new Array[Byte](bufferSize)
  private var bufferPosition = 0
  
  def read(): Int = { 
    Database.inTransaction {
      implicit val pgc = DB.pgConnection(Database.currentConnection)
      
      LO.withLargeObject(oid) { largeObject =>
        largeObject.read(buffer, 0, bufferSize)  
      }
      val b = buffer(bufferPosition)
      bufferPosition += 1
      
      b
    }
  }

}