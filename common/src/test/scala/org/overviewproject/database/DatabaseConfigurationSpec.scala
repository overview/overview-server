/*
 * DatabaseConfigurationSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, July 2012
 */

package org.overviewproject.database

import org.specs2.mutable.Specification
import org.specs2.specification._

class DatabaseConfigurationSpec extends Specification {
  
  "DatabaseConfiguration properties" should {

  
    "be read from datasource.default.url property, if set" in {
      val username = "overview"
      val password = "overview"      
      val host = "localhost"
      val database = "overview-test"

      
      val systemDefault = sys.props.get("datasource.default.url")
      
      val defaultDriver = "org.postgresql.Driver"
      val expectedUrl = "jdbc:postgresql://"+host+"/"+database
      
      val dbConfig = new DatabaseConfiguration()

      dbConfig.databaseUrl must beEqualTo(expectedUrl)
      dbConfig.username must beEqualTo(username)
      dbConfig.password must beEqualTo(password)
      dbConfig.databaseDriver must beEqualTo(defaultDriver) 
    }
    
    "handle real urls" in {
      val testDatabaseUrlProperty = "testdatasource.default.url"

      val username = "overview-user"
      val password = "overview123-"      
      val host = "localhost.my-domain.org:1244"
      val database = "overview-test"
     
      val databaseUrl = "postgres://"+username+":"+password+"@"+host+"/"+database        
      sys.props += testDatabaseUrlProperty -> databaseUrl
      
      val dbConfig = 
        new DatabaseConfiguration(defaultDatabaseUrlProperty = testDatabaseUrlProperty)
      
      sys.props -= testDatabaseUrlProperty
      val expectedUrl = "jdbc:postgresql://"+host+"/"+database
      
      dbConfig.databaseUrl must beEqualTo(expectedUrl)
      dbConfig.username must beEqualTo(username)
      dbConfig.password must beEqualTo(password)
      
    }
  }

}
