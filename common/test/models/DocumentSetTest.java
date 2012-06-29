package models;


import static org.fest.assertions.Assertions.*;

import org.junit.Test;

public class DocumentSetTest {

	@Test
	public void addChildren() {
	  DocumentSet documentSet = new DocumentSet();
	  Document document = new Document("title", "textUrl", "viewUrl");
	  
	  documentSet.addDocument(document);
	  
	  assertThat(documentSet.documents).hasSize(1)
	  								   .contains(document);
	  
	  assertThat(document.documentSet).isEqualTo(documentSet);
	}

}
