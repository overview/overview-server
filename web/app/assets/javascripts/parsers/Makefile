all: message_format.js csv.js

%.js : %.pegjs
	pegjs -e 'var r' $< $@.deleteme && \
		echo '(function() {' > $@ && \
		cat $@.deleteme >> $@ && \
		echo 'define(function() { return r; });})();' >> $@ && \
		rm -f $@.deleteme

clean:
	rm -f message_format.js
	rm -f csv.js
