/* 
 * DataSourceSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package database

import anorm._
import org.specs2.mutable.Specification
import java.sql.SQLException

class DataSourceSpec extends Specification {

  "DataSource" should {
    
    "provide connections until it is shutdown" in {
      
      val config = new DatabaseConfiguration()
      val dataSource = new DataSource(config)
      
      implicit val connection = dataSource.getConnection()
      
      val id = SQL("SELECT * from document_set").execute
      	
      connection.close()
      id must beTrue
      
      dataSource.shutdown()
      dataSource.getConnection() must throwA[SQLException]
    }
  }
}