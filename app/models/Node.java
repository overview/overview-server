package models;

import java.util.*;

import play.db.ebean.*;
import play.data.validation.Constraints.*;

import javax.persistence.*;

@Entity
public class Node extends Model {
    @Id
    public Long id;

    public String description;
}
