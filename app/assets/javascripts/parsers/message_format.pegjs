// Grammar derived from http://docs.oracle.com/javase/6/docs/api/java/text/MessageFormat.html

start = message_format

message_format = (format_element / s:string+ { return s.join(''); }) *

format_element
  = "{" i:argument_index "}" { return { index: i, format_type: 'string' }; }
  / "{" i:argument_index "," ft:simple_format_type "}" { return { index: i, format_type: ft }; }
  / "{" i:argument_index ",number," fs:number_format_style "}" { return { index: i, format_type: 'number', format_style: fs }; }
  / "{" i:argument_index ",date," fs:date_format_style "}" { return { index: i, format_type: 'date', format_style: fs }; }
  / "{" i:argument_index ",time," fs:time_format_style "}" { return { index: i, format_type: 'time', format_style: fs }; }
  / "{" i:argument_index ",choice," fs:choice_subformat_pattern "}" { return { index: i, format_type: 'choice', format_style: fs }; }

argument_index = d:[0-9]+ { return parseInt(d.join(''), 10); }

simple_format_type = "number" / "date" / "time"

number_format_style = "integer" / "currency" / "percent" / number_subformat_pattern

date_format_style = "short" / "medium" / "long" / "full"// / date_subformat_pattern

time_format_style = "short" / "medium" / "long" / "full"// / time_subformat_pattern

choice_subformat_pattern = choices:("|" / choice)+ { return choices.filter(function(choice) { return choice != '|'; }); }

choice = n:number ("#" / "<") f:submessage_format { return { limit: n, format: f }; }

submessage_format = (format_element / s:submessage_string+ { return s.join(''); })*

number = integer / float

integer = d:("-"? [0-9]+) { return parseInt(d.join(''), 10); }

float = d:("-"? [0-9]+ "." [0-9]+) { return parseFloat(d.join('')); }

number_subformat_pattern = "0.00" // TODO

string = "''" { return "'"; } / "'" s:quoted_string "'" { return s; } / s:unquoted_string { return s; }

quoted_string = c:[^']+ { return c.join(''); }

unquoted_string = c:[^'{]+ { return c.join(''); }

submessage_string = c:[^'{}|]+ { return c.join(''); } // a total guess. Docs don't say what this is.
