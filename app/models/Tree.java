package models;

import java.util.*;

import play.db.ebean.Model;


public class Tree extends Model {
	private static final long serialVersionUID = 1L;

	public Long id;
	
	public Node root;
	
	public Set<Document> documents = new HashSet<Document>();
	
}
