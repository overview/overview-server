package models;

import static org.fest.assertions.Assertions.*;

import java.util.List;
import org.junit.Test;



public class TreeTest  extends DatabaseTest {

	@Test
	public void saveToDatabase() {
	  Node root = new Node();
	  Node child = new Node();
	  
	  root.addChild(child);

	  Node grandChild = new Node();

	  child.addChild(grandChild);
			
	  Tree tree = new Tree();

	  tree.root = root;
			
	  tree.save();
			
	  Tree foundTree = Tree.find.byId(tree.id);
				
	  assertThat(foundTree.id).isEqualTo(tree.id);
	  assertThat(foundTree.root).isNotNull();
				
	  fakeLoadChildNodes(foundTree.root);
	  
	  assertThat(foundTree.root.children.size()).isEqualTo(1);
	  Node foundChild = foundTree.root.children.iterator().next();
	  assertThat(foundChild.id).isEqualTo(child.id);
				
	  fakeLoadChildNodes(foundChild);
	  
	  assertThat(foundChild.children.size()).isEqualTo(1);
				
	  Node foundGrandChild = foundChild.children.iterator().next();
	  assertThat(foundGrandChild.id).isEqualTo(grandChild.id);
	}

	// wrapping tests in transactions messes up loading
	private void fakeLoadChildNodes(Node node) {
	  List<Node> children = Node.find.where().eq("parent", node).findList();
		  
	  for (Node childNode: children) {
		node.addChild(childNode);
	  }
	}
}
		
	


