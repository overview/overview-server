package views;

import java.util.*;
import java.util.regex.*;

import javassist.runtime.DotClass;

import play.mvc.*;
import play.test.*;
import views.html.*;
import models.*;

import static play.test.Helpers.*;
import static org.fest.assertions.Assertions.*;

import org.junit.Test;

public class ViewDocumentSetTest {

	@Test
	public void queryInHeader() {
		String query = "this is the query";
		String header = "<h1>Query: " + query + " </h1>";
		
		List<Document> documents = new ArrayList<Document>();
		Content html = viewDocumentSet.render(query, documents);
		assertThat(contentAsString(html)).contains(header);
				
	}
	
	@Test 
	public void documentLinks() {
		
		String query = "this is the query";
		
		String doc1Title = "document1";
		String doc2Title = "document2";
		
		Pattern pattern = Pattern.compile(".*<li>\\s*" + doc1Title + "\\s*</li>.*", Pattern.DOTALL);		
		
		List<Document> documents = new ArrayList<Document>();
		documents.add(new Document("", doc1Title, ""));
		documents.add(new Document("", doc2Title, ""));
		Content html = viewDocumentSet.render(query, documents);
		
		Matcher matcher = pattern.matcher(html.body());
		assertThat(matcher.matches()).overridingErrorMessage(html.body() + "[" + matcher.pattern() + "]").isTrue();	
	}
	
	

}
