web: target/start -Dhttp.port=${PORT} -DapplyEvolutions.default=true -Ddb.default.url=${DATABASE_URL} -Ddb.default.driver=org.postgresql.Driver
worker: java -cp "target/staged/*" -Ddatasource.default.url=${DATABASE_URL} -Ddatasource.default.driver=org.postgresql.Driver JobHandler
