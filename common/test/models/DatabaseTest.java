package models;

import org.junit.After;
import org.junit.Before;

import com.avaje.ebean.Ebean;

import play.test.FakeApplication;
import static play.test.Helpers.*;

public class DatabaseTest {

  private FakeApplication application;

  @Before
  public void setupTransaction() {
	application = fakeApplication();
	start(application);
	
	Ebean.beginTransaction();
  }
  
  @After
  public void rollBackTransaction() {
	Ebean.rollbackTransaction();
	
	stop(application);
  }
  
  


}
