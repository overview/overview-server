package models;

import play.db.ebean.*;
import javax.persistence.*;


@Entity
public class Document extends Model {
	
	@Id
	public Long id;
	public String documentCloudId;
	public String title;
	public String canonicalUrl;
	
	public static Finder<Long, Document> find = new Finder(Long.class, Document.class);
	
	public Document(String documentCloudId, String title, String canonicalUrl) {
		this.documentCloudId = documentCloudId;
		this.title = title;
		this.canonicalUrl = canonicalUrl;
	}
}
