# To reindex documents

You can reindex Overview with zero downtime. Here's what you'll do:

1. Create your new index, with the correct mapping and settings
2. Run this command and wait for it to complete
3. Delete the old index

## 1. Create your new index

First, pick an index name. The rest of this example will assume that name is
`documents_v2`. (The index Overview creates by default is `documents_v1`.)

Now, create the index. Refer to
`common/src/main/resources/documents-mapping.json` and
`common/src/main/resources/documents-settings.json` to formulate this command:

```
curl -XPUT 'http://elasticsearch-host:9200/documents_v2/' -d '{
  "settings": {
    "analysis": {
      "analyzer": {
        "overview_text_analyzer": {
          "tokenizer": "standard",
          "filter": [ "icu_normalizer" ]
        }
      }
    }
  },
  "mappings": {
    "document": {
      "properties": {
        "_all":            { "type": "string", "analyzer": "overview_text_analyzer" },
        "document_set_id": { "type": "long" },
        "text":            { "type": "string", "analyzer": "overview_text_analyzer" },
        "supplied_id":     { "type": "string" },
        "title":           { "type": "string", "analyzer": "overview_text_analyzer" }
      }
    }
  }
}'
```

## 2. Run this command and wait

Our reindexing command runs completely independently of Overview. We'll run it through
`sbt`. You could just as easily run it with a plain `java` command and appropriate
classpath.

```
./sbt 'reindex-documents/run --database-url "postgres://overview:overview@localhost:9010/overview" --elasticsearch-url "localhost:9200" --index-name "documents_v2"'
```

## 3. Delete the old index

```
curl -XDELETE 'http://elasticsearch-host:9200/documents_v1/'
```
