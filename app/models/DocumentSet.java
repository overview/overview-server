package models;

import java.util.*;

import play.Logger;
import play.db.ebean.*;
import play.data.validation.Constraints.*;

import javax.persistence.*;


@Entity
public class DocumentSet extends Model {
    @Id
    public Long id;

    @Required
    public String query;

    
    @OneToMany(mappedBy="documentSetId", cascade=CascadeType.ALL)
    public List<DocumentReference> documentIds;
    
        
    public static Finder<Long, DocumentSet> find = new Finder(Long.class, DocumentSet.class);
    
    public DocumentSet() {
    	documentIds = new ArrayList<DocumentReference>();
    }
    
    public static List<DocumentSet> all()
    {
    	return find.all();
    }
    
    public static void create(DocumentSet documentSet) {
    	documentSet.save();
    }
    
    public static void delete(Long id) {
    	find.ref(id).delete();
    }
    
    public void addDocument(Document document) {
    	DocumentReference ref = new DocumentReference();
    	ref.documentId = document.id;
    	ref.documentSetId = this;
    	ref.save();
    	documentIds.add(ref);
    }
    
    public List<Document> getDocuments() {
    	List<Document> documents = new ArrayList<Document>();

    	for (DocumentReference ref : documentIds) {
    		documents.add(Document.find.byId(ref.documentId));
    	}
    	
    	return documents;
    }
    
   
}