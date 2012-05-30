package models;

import play.test.*;

import org.junit.*;

import static play.test.Helpers.*;
import static org.fest.assertions.Assertions.*;


import org.junit.Test;

public class DocumentTest {

	@Test
	public void saveDocument() {
		
		running(fakeApplication(inMemoryDatabase()), new Runnable() {
			public void run() {
				Document document = new Document("doccloud id", "title", "canonical url");
		
				document.save();
				Document storedDocument = Document.find.byId(document.id);
				
				assertThat(storedDocument.title).isEqualTo("title");
			}
		});
		
		
	}

}
