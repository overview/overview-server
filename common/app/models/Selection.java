package models;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Selection {
    public Tree tree;
    public Set<Node> nodes = new HashSet<Node>();
    public Set<Document> documents = new HashSet<Document>();
    public Set<Tag> tags = new HashSet<Tag>();

    public Selection(Tree tree) {
        this.tree = tree;
    }

    public List<Document> slice(long start, long end) {
        return new ArrayList<Document>();
    }

    public Long count() {
        return 0L;
    }
}
