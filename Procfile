web: target/start ${JAVA_OPTS} -Dconfig.resource=production.conf -Dhttp.port=${PORT} -DapplyEvolutions.default=true -Ddb.default.url=${DATABASE_URL} -Ddb.default.driver=org.postgresql.Driver -Dsmtp.mock=false
worker: java -cp "target/staged/*" ${JAVA_OPTS} -Ddatasource.default.url=${DATABASE_URL} -Ddatasource.default.driver=org.postgresql.Driver JobHandler
