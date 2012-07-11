package models;

import static org.fest.assertions.Assertions.*;

import java.util.Set;

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
