package models;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import play.data.validation.Constraints;
import play.db.ebean.*;
import scala.util.matching.Regex;

import javax.persistence.*;

@Entity
public class Document extends Model {
	private static final long serialVersionUID = 1L;

    @Id
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

	public static Finder<Long, Document> find = new Finder<Long, Document>(Long.class, Document.class);
	
	public Document(DocumentSet documentSet, String title, String textUrl, String viewUrl) {
    this.documentSet = documentSet;
		this.title = title;
		this.textUrl = textUrl;
		this.viewUrl = viewUrl;
		this.tags = new HashSet<Tag>();
	}
	
	public void setTags(String tagsString) {
		String[] tagNames = tagsString.split(",");
		
		Map<String, Tag> allTags = new HashMap<String, Tag>();
		
		for (Tag tag : documentSet.tags) {
			allTags.put(tag.name, tag);
		}
		
		tags.clear();
		for (String uglyTagName : tagNames) {
            String tagName = uglyTagName.trim();

			Tag tag = allTags.get(tagName);
			if (tag == null) {
				tag = new Tag(documentSet, tagName);
                tag.save(); // XXX find a way not to do this
			}

			tags.add(tag);
			//tag.documents.add(this);
		}
	}
}
