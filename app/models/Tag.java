package models;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import play.data.validation.Constraints.Required;
import play.db.ebean.Model;

@Table(uniqueConstraints=@UniqueConstraint(columnNames={"document_set_id", "name"}))
@Entity
public class Tag extends Model {
	private static final long serialVersionUID = 1L;

	@Id
	public Long id;
	
	@Required
	public String name;
	
	@ManyToOne
	public DocumentSet documentSet;
	
	@ManyToMany(mappedBy="tags")
	public Set<Document> documents = new HashSet<Document>();
	
	public static Finder<Long, Tag> find = new Finder<Long, Tag>(Long.class, Tag.class);

	public Tag(DocumentSet documentSet, String name) {
		this.name = name;
		this.documentSet = documentSet;
	}

    public String toString() {
        return "Tag[" + this.name + "]";
    }
}
