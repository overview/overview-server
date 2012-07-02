

import com.avaje.ebean.config.GlobalProperties

import org.specs2.mutable.Specification
import org.specs2.specification._





class DatabaseConfigurationSpec extends Specification {
  
  "DatabaseConfiguration properties" should {


    "be read from ebean.properties by default" in {
      val defaultDriver = GlobalProperties.get("datasource.default.databaseDriver", null)
      val defaultDatabaseUrl = GlobalProperties.get("datasource.default.databaseUrl", null)
      val defaultUsername = GlobalProperties.get("datasource.default.username", null)
      val defaultPassword = GlobalProperties.get("datasource.default.password", null)
      
      defaultDriver must not beNull; 
      defaultDatabaseUrl must not beNull;
      defaultUsername must not beNull;
      defaultPassword must not beNull;
      
      val dbConfig = new DatabaseConfiguration()
      
      dbConfig.databaseDriver must beEqualTo(defaultDriver)
      dbConfig.databaseUrl must beEqualTo(defaultDatabaseUrl)
      dbConfig.username must beEqualTo(defaultUsername)
      dbConfig.password must beEqualTo(defaultPassword)
    }
  
    "be read from datasource.default.url property, if set" in {
      val username = "DbUsername"
      val password = "DbPassword"      
      val host = "DbHost29.fo-bar.com"
      val database = "Database"

      val databaseUrl = "postgres://"+username+":"+password+"@"+host+"/"+database
    
      sys.props += ("datasource.default.url" -> databaseUrl)

      val defaultDriver = GlobalProperties.get("datasource.default.databaseDriver", null)
      val expectedUrl = "jdbc:postgresql://"+host+"/"+database
      
      val dbConfig = new DatabaseConfiguration()

      sys.props -= "datasource.default.url" 

      dbConfig.databaseUrl must beEqualTo(expectedUrl)
      dbConfig.username must beEqualTo(username)
      dbConfig.password must beEqualTo(password)
      dbConfig.databaseDriver must beEqualTo(defaultDriver) 
    }
  }

}