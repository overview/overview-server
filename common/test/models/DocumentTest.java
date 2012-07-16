package models;



import static org.fest.assertions.Assertions.*;

import java.util.Set;
import org.junit.Test;



public class DocumentTest extends DatabaseTest {

	@Test
	public void saveDocument() {
	  Document document = new Document("title", "http://text", "http://view");
	  document.save();
          
	  Document storedDocument = Document.find.byId(document.id);
				
	  assertThat(storedDocument.title).isEqualTo("title");
	}

  // TODO: Remove or fix this test when we start dealing with tags, since it duplicates 
  //       tests in DocumentSet  
  @Test
  public void createTags() {
	Document document = new Document("title", "http://text", "http://view");

	DocumentSet documentSet = new DocumentSet();
	documentSet.addDocument(document);
	Set<Tag> tags = documentSet.findOrCreateTags("  foo , bar");

	document.addTags(tags);

	documentSet.save();

	assertThat(document.tags).onProperty("name")
	                         .contains("foo")
	                         .contains("bar");

	documentSet.refresh();
	assertThat(documentSet.tags.size()).isEqualTo(2);

	assertThat(documentSet.tags).onProperty("name")
       							.contains("foo")
       							.contains("bar");
        
	assertThat(Tag.find.all()).onProperty("name")
       						  .contains("foo")
       						  .contains("bar");
  }
}
