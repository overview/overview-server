package models;

import java.util.*;

import org.junit.Before;
import org.junit.Test;

import static org.fest.assertions.Assertions.*;

public class NodeTest extends DatabaseTest {

	private Node root;
	
	@Before
	public void createTree() {
		root = new Node();
		
		root.description = "description";
		
		for (long i = 10l; i < 13l; i++) {
			Node level1ChildNode = new Node();
			root.addChild(level1ChildNode);
						
			for (int j = 1; j < 5l; j++) {
				Node level2ChildNode = new Node();
				level2ChildNode.description = "bla";
				level1ChildNode.addChild(level2ChildNode);
			}
		}
		
		root.save();
	}
	
	
	
	@Test
	public void getFirstLevelNodesBreadthFirst() {
	  List<Node> onlyLevel1 = root.getNodesBreadthFirst(4);
	  assertThat(onlyLevel1.size()).isEqualTo(4);
	  assertThat(onlyLevel1.get(0).id).isEqualTo(root.id);
				
	  for (int i = 1; i < 4; i++) {
		  assertThat(root.children.contains(onlyLevel1.get(i)));
	  }
	}
	
	@Test
	public void partiallyIncludeSecondLevel() {
	  List<Node> halfLevel2 = root.getNodesBreadthFirst(9);
	  assertThat(halfLevel2.size()).isEqualTo(9);
	}

	@Test 
	public void includeMoreThanWhole() {
	  List<Node> wholeTree = root.getNodesBreadthFirst(26);
	  assertThat(wholeTree.size()).isEqualTo(16);
	}
	
	@Test
	public void saveToDatabase() {
	  for (int i = 0; i < 5; i++) {
		Document document = new Document("title:" + i, "textUrl-" + i, "viewUrl-" + i);
		root.addDocument(document);
	  }
	  root.save();

	  Node foundNode = Node.find.byId(root.id);
				
	  assertThat(foundNode.id).isEqualTo(root.id);
	  assertThat(foundNode.description).isEqualTo(root.description);
	  assertThat(foundNode.children).isNotNull();
	  assertThat(foundNode.children.size()).isEqualTo(root.children.size());
	  
	  fakeLoadSecondLevelNodes(foundNode);
	  
	  for (Node child: foundNode.children) {
		assertThat(child.children.size()).isEqualTo(4);
	  }
	  assertThat(foundNode.documents.size()).isEqualTo(5);
	}

	@Test
	public void loadChildNodesFromDatabase() {
	  Node foundRoot = Node.find.byId(root.id);
	  
	  fakeLoadSecondLevelNodes(foundRoot);
	  
	  List<Node> descendants = foundRoot.getNodesBreadthFirst(12);
				
	  assertThat(descendants.size()).isEqualTo(12);
	}
	
	// when we wrap tests in transactions, the entire node tree does not get loaded
	private void fakeLoadSecondLevelNodes(Node node)
	{
	  for (Node level1Child: node.children) {
		  List<Node> unloadedChildren = Node.find.where().eq("parent", level1Child).findList();
		  
		  for (Node level2Child: unloadedChildren) {
			  level1Child.addChild(level2Child);
		  }
		 
	  }
	}
}


