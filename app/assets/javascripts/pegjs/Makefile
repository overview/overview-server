all: message_format.js csv.js

message_format.js: message_format.pegjs
	pegjs -e window.message_format_parser $< $@

csv.js: csv.pegjs
	pegjs -e window.csv_parser --track-line-and-column $< $@

clean:
	rm -f message_format.js
	rm -f csv.js
