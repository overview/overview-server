package models;

import java.util.*;

import play.db.ebean.*;
import play.data.validation.Constraints.*;

import javax.persistence.*;

@Entity
public class DocumentSet extends Model {
    private static final long serialVersionUID = 1L;

    @Id
	@GeneratedValue(strategy=GenerationType.SEQUENCE, generator="document_set_id_seq")
    public Long id;

    @Required
    public String query;

    @OneToMany(cascade=CascadeType.ALL)
    public Set<Tag> tags = new HashSet<Tag>();

    @OneToMany(cascade=CascadeType.ALL)
    public Set<Document> documents = new HashSet<Document>();

    @ManyToMany(mappedBy="documents")
    public Set<Node> nodes = new HashSet<Node>();

    public static Finder<Long, DocumentSet> find = new Finder<Long, DocumentSet>(Long.class, DocumentSet.class);
    
    public void addDocument(Document document) {
    	documents.add(document);
    	document.documentSet = this;
    }
    
    // TODO: make this clean when we start dealing with tags
    public Set<Tag> findOrCreateTags(String tagString) {
      String[] tagNames = tagString.trim().split("\\s*,\\s*");
      
      Map<String, Tag> allTags = new HashMap<String, Tag>();
      
      for (Tag tag: tags) {
    	allTags.put(tag.name, tag);
      }
      
      Set<Tag> foundTags = new HashSet<Tag>();
      
      for (String name: tagNames) {
    	Tag foundTag;
    	
    	if (allTags.containsKey(name)) {
    	  foundTag = allTags.get(name);
    	}
    	else {
    	  foundTag = new Tag(this, name);
    	  tags.add(foundTag);
    	}
    	
    	foundTags.add(foundTag);
      }
      
      return foundTags;
    }
}
