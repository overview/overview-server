
import scala.collection.JavaConversions._
import scala.io.Source
import com.avaje.ebean.{Ebean, EbeanServerFactory}
import com.avaje.ebean.config.{ServerConfig, DataSourceConfig}
import models.{DocumentSet,DocumentSetCreationJob}
import models.DocumentSetCreationJob.JobState

object JobHandler {
  def main(args: Array[String]) {

	configureDatabaseConnection
	
	while (true) {
      Thread.sleep(500)
      val submittedJobs = DocumentSetCreationJob.find.where.eq("state", JobState.Submitted).findList.toSeq
      
      for (j <- submittedJobs) {
    	println(j.query)
        println(j.state)
        j.setState(JobState.InProgress);
        
        j.save
        val documentSet = new DocumentSet
        documentSet.setQuery(j.query)
        documentSet.save
        println("documentSet: " + documentSet.query)
        
        val indexer = new DocumentSetIndexer(documentSet)
        indexer.createDocuments
        indexer.indexDocuments
        
        j.setState(JobState.Complete)
        j.save
      }
      
    }

  }

  def configureDatabaseConnection() = {
    val databaseConfig = new DatabaseConfiguration()
    
    val config = new ServerConfig();  
	config.setName("default");  
	
	  // Define DataSource parameters  
	val postgresDb = new DataSourceConfig()  
	postgresDb.setDriver(databaseConfig.databaseDriver)  
	postgresDb.setUsername(databaseConfig.username)  
	postgresDb.setPassword(databaseConfig.password) 
	postgresDb.setUrl(databaseConfig.databaseUrl)
	
	postgresDb.setHeartbeatSql("select count(*) from tree")
  
	config.setDataSourceConfig(postgresDb)
  
	// set DDL options...  
	config.setDdlGenerate(false)  
	config.setDdlRun(false)	 
  
	config.setDefaultServer(true)  
	config.setRegister(true)  
	
	val server = EbeanServerFactory.create(config)

  }
}
