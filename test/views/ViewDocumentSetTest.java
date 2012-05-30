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
		
		List<String> titles = Arrays.asList("document1", "document2");
		
		String patternString = ".*";
		for (String title : titles) {
			patternString += "<li>\\s*" + 
					"<a href=\""title + "\\s*</li>.*";
		}
		Pattern pattern = Pattern.compile(patternString, Pattern.DOTALL);		
		
		List<Document> documents = new ArrayList<Document>();
		documents.add(new Document("", titles.get(0), ""));
		documents.add(new Document("", titles.get(1), ""));
		Content html = viewDocumentSet.render(query, documents);
		
		Matcher matcher = pattern.matcher(html.body());
		assertThat(matcher.matches()).overridingErrorMessage(html.body() + "[" + matcher.pattern() + "]").isTrue();	
	}
	
	

}
