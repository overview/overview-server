package models;


import static org.fest.assertions.Assertions.*;


import com.avaje.ebean.Ebean;
import org.junit.Test;

import static play.test.Helpers.*;


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
	
	@Test
	public void saveToDatabase() {
	  running (fakeApplication(), new Runnable() {
		
		public void run() {
	  
		  Ebean.beginTransaction();
		
		  DocumentSet documentSet = new DocumentSet();
		
		  for (int i = 0; i < 3; i++) {
			Document document = new Document("title" + i, "textUrl" + i, "viewUrl" + i);
			documentSet.addDocument(document);
		  }
		  
		
		  documentSet.save();

		  DocumentSet foundDocumentSet = DocumentSet.find.byId(documentSet.id);
		
		  assertThat(foundDocumentSet).isNotNull();
		  assertThat(foundDocumentSet.documents).hasSize(3);
		  assertThat(foundDocumentSet.documents).onProperty("title")
		  										.contains("title0", "title1", "title2");
		  
		  Ebean.endTransaction();
		}
	  });
	}

}
