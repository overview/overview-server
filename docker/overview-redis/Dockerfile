FROM redis:4.0.2-alpine

COPY redis.conf /etc/redis/redis.conf

# Port 6379: redis connections from overview-web

CMD [ "redis-server", "/etc/redis/redis.conf" ]
