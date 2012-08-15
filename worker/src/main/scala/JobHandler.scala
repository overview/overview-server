

import com.avaje.ebean.{Ebean, EbeanServerFactory}
import com.avaje.ebean.config.{ServerConfig, DataSourceConfig}
import com.jolbox.bonecp._

import database.{DatabaseConfiguration, DataSource, DB}
import persistence._
import persistence.DocumentSetCreationJobState._

object JobHandler {
  def main(args: Array[String]) {

    val config = new DatabaseConfiguration()
	val dataSource = new DataSource(config)
	
    DB.connect(dataSource)
    
	while (true) {
      Thread.sleep(500) 
      

      val submittedJobs: Seq[PersistentDocumentSetCreationJob] = DB.withConnection { implicit connection =>
       PersistentDocumentSetCreationJob.findAllSubmitted
      }

      for (j <- submittedJobs) {
        
        val documentSetWriter = new DocumentSetWriter()
        
        val documentSetId = DB.withConnection { implicit connection => 
          documentSetWriter.write(j.query)
        }


        println("Created document set for query: " + j.query)

        val documentWriter = new DocumentWriter(documentSetId)
        val nodeWriter = new NodeWriter(documentSetId)
        val indexer = 
          new clustering.DocumentSetIndexer(j.query, nodeWriter, documentWriter)
        
        val tree = indexer.BuildTree()

        j.state = Complete
        DB.withConnection { implicit connection =>
          j.update
        }
      }
      
    }
  }
}
