package models;

import java.util.*;

import play.db.ebean.*;
import play.data.validation.Constraints.*;
import javax.persistence.*;

@Entity
public class DocumentSet extends Model {
    @Id
    public Long id;

    @Required
    public String query;

    public static Finder<Long, DocumentSet> find = new Finder(Long.class, DocumentSet.class);
    
    public static List<DocumentSet> all()
    {
    	return find.all();
    }
    
    public static void create(DocumentSet documentSet) {
    	documentSet.save();
    }
}