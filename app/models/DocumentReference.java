package models;

import play.db.ebean.Model;

import javax.persistence.*;

@Entity
public class DocumentReference extends Model {
	
	@Id
	public Long id;
	
	@ManyToOne(fetch=FetchType.LAZY)
	public DocumentSet documentSetId;
	
	public Long documentId;
	
}
