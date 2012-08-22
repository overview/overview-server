/***
 * JobHandler.scala
 * 
 * Overview Project,June 2012
 * @author Jonas Karlsson
 */

package overview.clustering

import akka.actor._
import akka.util.duration._
import com.jolbox.bonecp._

import database.{DatabaseConfiguration, DataSource, DB}
import overview.logging._
import overview.WorkerActorSystem
import persistence._
import persistence.DocumentSetCreationJobState._

case class ScanForJobs()
case class JobDone()
case class JobProgress(job:PersistentDocumentSetCreationJob, percentComplete:Int, stageName:String, statusText:String)

class JobHandler extends Actor {
    
    val pollingInterval = 500 milliseconds
    
    // waits specified interval, then send self a message to look for more jobs
    def waitAndThenScan : Unit = {
      WorkerActorSystem().scheduler.scheduleOnce(pollingInterval, self, ScanForJobs);
    }
    
    // Run each job currently listed in the database
    def handleJobs : Unit  = {
    
      val submittedJobs: Seq[PersistentDocumentSetCreationJob] = DB.withConnection { implicit connection =>
        PersistentDocumentSetCreationJob.findAllSubmitted
      }

      for (j <- submittedJobs) {
        handleSingleJob(j)
      }
    }
    
    // Run a single job
    def handleSingleJob(j:PersistentDocumentSetCreationJob) : Unit = {
      j.state = InProgress
      DB.withConnection { implicit connection =>
        j.update
      }
      val documentSetId = j.documentSetId

      val documentWriter = new DocumentWriter(documentSetId)
      val nodeWriter = new NodeWriter(documentSetId)
      val query = DB.withConnection { implicit connection => 
        DocumentSetLoader.loadQuery(j.documentSetId).get
      }	
      Logger.info("Indexing query: " + query)
      val indexer = new DocumentSetIndexer(new DocumentCloudSource(query), nodeWriter, documentWriter, self)
      val tree = indexer.BuildTree()

      j.state = Complete
      DB.withConnection { implicit connection =>
        j.update
      }
    }
      
    def receive = { 
      case ScanForJobs =>
       handleJobs
       waitAndThenScan
        
      case prog:JobProgress =>
        // TODO write status to DB here, for display by client
        Logger.info("Job progress: " + prog.stageName + ", " + prog.percentComplete + "% done. " + prog.statusText)      
    }
  }

  
object JobHandler {
  
  def main(args: Array[String]) {

    val config = new DatabaseConfiguration()
	  val dataSource = new DataSource(config)
	
    DB.connect(dataSource)
    
    // Create the job handling actor and kick it off
    val jobHandler = WorkerActorSystem().actorOf( Props(new JobHandler), name = "jobHandler")

    jobHandler ! ScanForJobs
    
    while (true) {
      Thread.sleep(100000)    // sleep this thread forever, as the actor is self-looping
    }
  }
  
}
