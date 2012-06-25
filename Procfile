web: target/start -Dhttp.port=${PORT} -DapplyEvolutions.default=true -Ddb.default.url=${DATABASE_URL} -Ddb.default.driver=org.postgresql.Driver
worker: java -cp "target/staged/*" -Ddb.default.url=${DATABASE_URL} -Ddb.default.driver=org.postgresql.Driver JobHandler
