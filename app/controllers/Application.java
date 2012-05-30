package controllers;

import org.codehaus.jackson.JsonNode;

import play.*;
import play.data.*;
import play.libs.F.Function;
import play.libs.WS;
import play.mvc.*;

import views.html.*;

import models.*;

import java.util.*;

public class Application extends Controller {

    static Form<DocumentSet> documentSetForm = form(DocumentSet.class);

    public static Result index() {
    	return ok(index.render("The Overview Project"));
    }

    public static Result showDocumentSets() {
    	return ok(documentSets.render(DocumentSet.all(), documentSetForm));
    }

    public static Result newDocumentSet() {
    	Form<DocumentSet> filledForm = documentSetForm.bindFromRequest();
    	
    	if(filledForm.hasErrors()) {
    		return badRequest(
    				views.html.documentSets.render(DocumentSet.all(), filledForm)
    				);
    	}
    	else {
    		DocumentSet.create(filledForm.get());

    		return redirect(routes.Application.showDocumentSets());
    	}
    }
    
    public static Result deleteDocumentSet(Long id) {
    	DocumentSet.delete(id);
    	
    	return redirect(routes.Application.showDocumentSets());
    }

    
    public static Result viewDocumentSet(Long id) {
    	DocumentSet documentSet = DocumentSet.find.byId(id);
    	final String queryString = documentSet.query;
    	String documentCloudQuery = "http://www.documentcloud.org/api/search.json?q=" + queryString; 
    	
    	return async(
    			WS.url(documentCloudQuery).get().map(
    					new Function<WS.Response, Result>() {
    						public Result apply(WS.Response response) {
    							JsonNode documentReferences = response.asJson().get("documents");
    							List<Document> documents = new ArrayList<Document>();
/*    							
    							for (JsonNode document : documentReferences) {
    								documents.add(document.ring());
    							}
  */  							
    							return ok(views.html.viewDocumentSet.render(queryString, documents));
    						}
    					}
    				)
    			);
    			
    }
}