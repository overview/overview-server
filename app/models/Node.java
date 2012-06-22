package models;

import java.util.*;
import javax.persistence.*;


import play.db.ebean.*;

@Entity
public class Node extends Model {
	private static final long serialVersionUID = 1L;

	@Id
    public Long id;

    public String description;
    
    @ManyToOne
    public Node parent;
    
    @OneToMany(cascade=CascadeType.ALL,mappedBy="parent")
    public Set<Node> children = new HashSet<Node>();
 
    @ManyToMany(cascade=CascadeType.ALL)
    public Set<Document> documents = new HashSet<Document>();
    
    public static Finder<Long, Node> find = new Finder<Long, Node>(Long.class, Node.class);
    
    public void addDocument(Document document)
    {
    	document.nodes.add(this);
    	documents.add(document);
    }
    
    public void addChild(Node child) 
    {
    	child.parent = this;
    	children.add(child);
    }
    
    public List<Node> getNodesBreadthFirst(int maxNumberOfNodes)
    {
    	List<Node> nodes = new ArrayList<Node>();
    	
    	List<Node> nodesToExpand = new ArrayList<Node>();
    	nodesToExpand.add(this);
    	boolean continueExpanding = true;
    	
    	while (continueExpanding) {
    		nodesToExpand.get(0).refresh();
    		nodes.add(nodesToExpand.get(0));
    		nodesToExpand.addAll(nodesToExpand.get(0).children);
    		nodesToExpand.remove(0);

    		continueExpanding = nodes.size() < maxNumberOfNodes &&
    							nodesToExpand.size() > 0;
    	}
    	
    	return nodes;
    }
}
