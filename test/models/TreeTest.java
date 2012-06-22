package models;

import org.junit.Test;

import static play.test.Helpers.*;
import static org.fest.assertions.Assertions.*;


public class TreeTest {

	@Test
	public void saveToDatabase() {
		running(fakeApplication(inMemoryDatabase()), new Runnable() {

			public void run() {
				Node root = new Node();
				root.id = 1l;
				Node child = new Node();
				child.id = 2l;
				root.addChild(child);
				Node grandChild = new Node();
				grandChild.id = 3l;
				child.addChild(grandChild);
			
				Tree tree = new Tree();
				tree.id = 100l;
				tree.root = root;
			
				tree.save();
			
				Tree foundTree = Tree.find.byId(tree.id);
				foundTree.refresh();
				
				assertThat(foundTree.id).isEqualTo(tree.id);
				assertThat(foundTree.root).isNotNull();
				
				foundTree.root.refresh();
				
				assertThat(foundTree.root.children.size()).isEqualTo(1);
				Node foundChild = foundTree.root.children.iterator().next();
				assertThat(foundChild.id).isEqualTo(child.id);
				
				foundChild.refresh();
				assertThat(foundChild.children.size()).isEqualTo(1);
				
				Node foundGrandChild = foundChild.children.iterator().next();
				assertThat(foundGrandChild.id).isEqualTo(grandChild.id);

			}
		});
	}
}
		
	


