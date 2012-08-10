package helpers


import anorm._
import org.specs2.mutable.Around
import org.specs2.execute.Result
import org.squeryl.adapters.PostgreSqlAdapter
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.SessionFactory
import org.squeryl.Session


trait DbTestContext extends Around {
  Class.forName("org.postgresql.Driver");

  SessionFactory.concreteFactory = Some(() =>
    Session.create(
      java.sql.DriverManager.getConnection("jdbc:postgresql://localhost/overview-test",
        "overview", "overview"),
      new PostgreSqlAdapter))

  lazy implicit val connection = Session.currentSession.connection
  
  def around[T <% Result](test: => T) = {
         
    inTransaction {
      SQL("TRUNCATE TABLE document_set CASCADE").execute()
      val result = test
      connection.rollback()
      
      result
    }
  }
  
}