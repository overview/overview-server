package models;

import javax.persistence.*;

import play.db.ebean.*;


@Entity
public class DocumentSetCreationJob extends Model {
	private static final long serialVersionUID = -1253329615859447897L;
	
	public enum JobState {Submitted, InProgress, Complete};
	
	@Id
	public long id;
	
	public String query;
	
	public JobState state = JobState.Submitted;
	
	public static Finder<Long, DocumentSetCreationJob> find = 
			new Finder<Long, DocumentSetCreationJob>(Long.class, DocumentSetCreationJob.class);
	
	public DocumentSetCreationJob(String query) {
		this.query = query;
	}
	

}
