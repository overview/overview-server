package models;

import java.util.*;
import javax.persistence.*;


import play.db.ebean.*;
import play.db.ebean.Model.*; // for some reason this is needed, otherwise clean compile fails


@Entity
public class Tree extends Model {
	private static final long serialVersionUID = 1L;

	@Id
	public Long id;
	
	@OneToOne(cascade=CascadeType.ALL)
	public Node root;
	
	public Set<Document> documents = new HashSet<Document>();
	
	public static Finder<Long, Tree> find = new Finder<Long, Tree>(Long.class, Tree.class);
	
}
