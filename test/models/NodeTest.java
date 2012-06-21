package models;

import java.util.*;


import org.junit.Before;
import org.junit.Test;

import static org.fest.assertions.Assertions.*;

public class NodeTest {

	private Node root;
	
	@Before
	public void createTree() {
		root = new Node();
		
		root.id = 1l;
		
		for (long i = 10l; i < 13l; i++) {
			Node level1ChildNode = new Node();
			level1ChildNode.id = i;
			root.children.add(level1ChildNode);
			
			for (int j = 1; j < 5l; j++) {
				Node level2ChildNode = new Node();
				level2ChildNode.id = 10L * i + j;
				level1ChildNode.children.add(level2ChildNode);
			}
		}
				
	}
	
	@Test
	public void getFistLevelNodesBreadthFirst() {
				
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

		int firstChildChildrenStart = root.children.size() + 1;
		int secondChildChildrenStart = root.children.size() + 1 + 4;
		
		for (int i = firstChildChildrenStart; i < secondChildChildrenStart; i++) {
			long parentId = halfLevel2.get(i).id / 10l;
			assertThat(parentId).isEqualTo(halfLevel2.get(1).id);
		}
		
		for (int i = secondChildChildrenStart; i < 9; i++) {
			long parentId = halfLevel2.get(i).id / 10l;
			assertThat(parentId).isEqualTo(halfLevel2.get(2).id);
		}

	}
	
	@Test
	public void includeMoreThanWhole() {
		List<Node> wholeTree = root.getNodesBreadthFirst(26);
		assertThat(wholeTree.size()).isEqualTo(16);
		
	}

}


