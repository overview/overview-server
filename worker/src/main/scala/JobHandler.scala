
import scala.collection.JavaConversions._

import com.avaje.ebean.Ebean;

import models.DocumentSetCreationJob
import models.DocumentSetCreationJob.JobState

object JobHandler {
  def main(args: Array[String]) {
	  
    while (true) {
      Thread.sleep(500)
      val submittedJobs = DocumentSetCreationJob.find.where.eq("state", JobState.Submitted).findList.toSeq
      
      for (j <- submittedJobs) {
        println(j.query)
        println(j.state)
        j.setState(JobState.InProgress);
        
        j.save();
      }
      
    }

  }


}
