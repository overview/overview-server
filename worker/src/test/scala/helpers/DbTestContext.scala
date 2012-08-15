package helpers



import org.specs2.mutable.Around
import org.specs2.execute.Result
import org.squeryl.adapters.PostgreSqlAdapter
import database.DB


trait DbTestContext extends Around {
  lazy implicit val connection = DB.getConnection()
  
  def around[T <% Result](test: => T) = {
    try {
      connection.setAutoCommit(false)
      test
    }
    finally {
      connection.rollback()
      connection.close()
    }
  }
  
}
    
