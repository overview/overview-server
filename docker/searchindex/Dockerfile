FROM elasticsearch:1.7

COPY elasticsearch.yml /usr/share/elasticsearch/config/elasticsearch.yml

RUN plugin -i elasticsearch/elasticsearch-analysis-icu/2.7.0
