all: message_format.js

message_format.js: message_format.pegjs
	pegjs -e window.message_format_parser $< $@

clean:
	rm -f message_format.js
