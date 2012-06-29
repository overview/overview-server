package models;


import static play.test.Helpers.*;
import static org.fest.assertions.Assertions.*;

import java.util.Set;


import org.junit.Test;

public class DocumentTest {

	@Test
	public void saveDocument() {
	  running(fakeApplication(inMemoryDatabase()), new Runnable() {
		public void run() {
          Document document = new Document("title", "http://text", "http://view");
          document.save();
          
          Document storedDocument = Document.find.byId(document.id);
				
          assertThat(storedDocument.title).isEqualTo("title");
	    }
	  });
	}

  @Test
  public void createTags() {
    running(fakeApplication(inMemoryDatabase()), new Runnable() {
      public void run() {

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
    });
  }
}
