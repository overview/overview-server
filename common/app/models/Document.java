package models;


import java.util.HashSet;
import java.util.Set;

import play.data.validation.Constraints;
import play.db.ebean.*;

import javax.persistence.*;

@Entity
public class Document extends Model {
	private static final long serialVersionUID = 1L;

    @Id
	@GeneratedValue(strategy=GenerationType.SEQUENCE, generator="document_id_seq")
    public Long id;

    @Constraints.Required
    public String title;

    @Constraints.Required
    public String textUrl;

    @Constraints.Required
    public String viewUrl;
    
    @ManyToOne
    public DocumentSet documentSet;
    
    @ManyToMany(cascade=CascadeType.ALL)
    public Set<Tag> tags;

    @ManyToMany(mappedBy="documents")
    public Set<Node> nodes;
    
	public static Finder<Long, Document> find = new Finder<Long, Document>(Long.class, Document.class);
	
	
	public Document(String title, String textUrl, String viewUrl) {
      this.title = title;
	  this.textUrl = textUrl;
	  this.viewUrl = viewUrl;
	  this.tags = new HashSet<Tag>();
	}
	
	public void addTags(Set<Tag> newTags) {
      tags.addAll(newTags);
	}
}
