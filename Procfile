worker: env JAVA_OPTS=$DATABASE_PROPS ./startHelper worker JobHandler
documentSetWorker: env JAVA_OPTS=$DATABASE_PROPS ./startHelper documentset-worker org.overviewproject.DocumentSetWorker
queue: env JAVA_OPTS=$APOLLO_PROPS ./startHelper message-broker org.apache.activemq.apollo.boot.Apollo documentset-worker/lib org.apache.activemq.apollo.cli.Apollo run






