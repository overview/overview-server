/*
 * DatabaseConfiguration.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, June 2012
 */

package database

import com.avaje.ebean.config.GlobalProperties

/**
 * Sets up properties needed to configure the database.
 * Reads datasource.default.url in Play's format http://user:password@host/database
 * and converts to jdbc:postgresql://host/database, with user and password in 
 * separate configuration variables.
 */
class DatabaseConfiguration {
  val DatabaseDriverProperty = "datasource.default.databaseDriver"
  val DatabaseUrlProperty =    "datasource.default.databaseUrl"
  val UsernameProperty =       "datasource.default.username"
  val PasswordProperty =       "datasource.default.password"
  val DATABASE_URL = 		   "datasource.default.url"
   
  val databaseDriver = "org.postgresql.Driver"
  val (databaseUrl, username, password) = readSettings()
    
  def readSettings() : (String, String, String) = {
	val databaseSetting = sys.props.get(DATABASE_URL)

	databaseSetting match {
      case Some(databaseInfo) => {
        val urlPattern = "\\w+://(\\w+):(\\w+)@([\\w-.]+)/([\\w-]+)".r
    
        val urlPattern(user, password, host, database) = databaseInfo
        ("jdbc:postgresql://"+host+"/"+database, user, password)
      }
      case None => {
        (GlobalProperties.get(DatabaseUrlProperty, null),
         GlobalProperties.get(UsernameProperty, null),
         GlobalProperties.get(PasswordProperty, null))
      }
	}
  }
}