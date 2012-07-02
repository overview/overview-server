package models;

import static org.fest.assertions.Assertions.*;
import static play.test.Helpers.*;

import java.util.Set;

import com.avaje.ebean.Ebean;
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
	
	@Test
	public void findTags() {
	  
	  DocumentSet documentSet = new DocumentSet();
	  
	  Set<Tag> tags = documentSet.findOrCreateTags("  foo, bar  , baz  ");
	  
	  assertThat(tags).hasSize(3);
	  assertThat(tags).onProperty("name")
	                  .contains("foo", "bar", "baz");
	  
	  Set<Tag> moreTags = documentSet.findOrCreateTags("foo, faz, baz");
	  assertThat(moreTags).hasSize(3);
	  assertThat(moreTags).onProperty("name")
	                      .contains("foo", "faz", "baz");

	  assertThat(documentSet.tags).hasSize(4);
	}

}
