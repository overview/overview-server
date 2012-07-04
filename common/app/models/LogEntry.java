package models;

import play.db.ebean.*;
import play.data.validation.Constraints.*;

import javax.persistence.*;

import org.joda.time.DateTime;

@Entity
public class LogEntry extends Model {
	private static final long serialVersionUID = 1L;

    @Id
    public Long id;

    @Required
    @ManyToOne
    public DocumentSet documentSet;

    @Required
    public String username = "Test User";

    @Required
    public DateTime date;

    @Required
    public String component;

    @Required
    public String action;

    public String details;
}
