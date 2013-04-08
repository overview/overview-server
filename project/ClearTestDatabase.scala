import java.sql.{ Connection, Driver, DriverManager }
import java.util.Properties

object ClearTestDatabase {
  val Url = "jdbc:postgresql://localhost/overview-test"
  val Username = "overview"
  val Password = "overview"

  private def SQL(sql: String)(implicit connection: Connection) = {
    connection.createStatement.execute(sql)
  }

  /** Gets a database connection.
    *
    * This is harder than you'd expect, because we have to operate within the
    * chosen ClassLoader.
    */
  private def getConnection(loader: ClassLoader) : Connection = {
    val properties = new Properties()
    properties.setProperty("user", Username)
    properties.setProperty("password", Password)

    val driver = Class.forName("org.postgresql.Driver", true, loader).newInstance.asInstanceOf[Driver]
    driver.connect(Url, properties)
  }

  /** Clears all tables in the database and adds an admin user.
    */
  def apply(loader: ClassLoader) {
    implicit val connection : Connection = getConnection(loader)
    SQL("SELECT lo_unlink(contents_oid) FROM upload")
    SQL("TRUNCATE TABLE upload CASCADE")
    SQL("TRUNCATE TABLE document_set CASCADE")
    SQL("TRUNCATE TABLE \"user\" CASCADE")
    SQL("INSERT INTO \"user\" (id, email, role, password_hash, confirmed_at, email_subscriber) VALUES (1, 'admin@overview-project.org', 2, '$2a$07$ZNI3MdA1MK7Td2w1EKpl5u38nll/MvlaRfZn0S8HLerNuP2hoD5JW', TIMESTAMP '1970-01-01 00:00:00', FALSE)")
    connection.close()
  }
}
