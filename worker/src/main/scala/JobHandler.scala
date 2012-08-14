
import scala.collection.JavaConversions._
import scala.io.Source
import com.avaje.ebean.{Ebean, EbeanServerFactory}
import com.avaje.ebean.config.{ServerConfig, DataSourceConfig}
import models.{DocumentSet,DocumentSetCreationJob}
import models.DocumentSetCreationJob.JobState
import writers.NodeWriter

object JobHandler {
  def main(args: Array[String]) {

	val server = configureDatabaseConnection
	
	while (true) {
      Thread.sleep(500)
      val submittedJobs = DocumentSetCreationJob.find.where.eq("state", JobState.Submitted).findList.toSeq
      
      for (j <- submittedJobs) {
        j.setState(JobState.InProgress);
        
        j.save
        val documentSet = new DocumentSet
        documentSet.setQuery(j.query)
        documentSet.save
        println("Created document set for query: " + documentSet.query)
        
        val indexer = new clustering.DocumentSetIndexer(documentSet)
        val tree = indexer.BuildTree(server)

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
	
	postgresDb.setHeartbeatSql("select 1")
  
	config.setDataSourceConfig(postgresDb)
  
	// set DDL options...  
	config.setDdlGenerate(false)  
	config.setDdlRun(false)	 
  
	config.setDefaultServer(true)  
	config.setRegister(true)  
	
	EbeanServerFactory.create(config)

  }
}
