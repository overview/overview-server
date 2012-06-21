package models;

import java.util.*;

import play.db.ebean.*;

import javax.persistence.*;

@Entity
public class Node extends Model {
	private static final long serialVersionUID = 1L;

	@Id
    public Long id;

    public String description;
    
    public Set<Node> children = new HashSet<Node>();
 
    public Set<Document> documents = new HashSet<Document>();
    
    public List<Node> getNodesBreadthFirst(int maxNumberOfNodes)
    {
    	List<Node> nodes = new ArrayList<Node>();
    	
    	List<Node> nodesToExpand = new ArrayList<Node>();
    	nodesToExpand.add(this);
    	boolean continueExpanding = true;
    	
    	while (continueExpanding) {
    		nodes.add(nodesToExpand.get(0));
    		nodesToExpand.addAll(nodesToExpand.get(0).children);
    		nodesToExpand.remove(0);

    		continueExpanding = nodes.size() < maxNumberOfNodes &&
    							nodesToExpand.size() > 0;
    	}
    	
    	return nodes;
    }
}
