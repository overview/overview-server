// CSV format, with defaults (separator: , quote: ")
//
// Grammar from http://tools.ietf.org/html/rfc4180#page-3
//
// With changes from <https://github.com/overview/overview-server/wiki/CSV-parsing>:
// * We allow Unicode, not just ASCII
// * We require a header
// * We allow blank lines (anytime after the header)
// * We allow "\r" or "\n" alone to divide lines, not just "\r\n"
// * We allow "\v", "\t" and "\f"
// * We allow truncated files

start = file

file =
  header:header
  records:(CRLF+ record:record { return record; })*
  CRLF*
  { return { header: header, records: records.filter(function(r) { return r.length > 1 || r[0].length > 0; }) }; }

header =
  first:name
  others:(COMMA name:name { return name; })*
  { return [first].concat(others) }

record =
  first:field
  others:(COMMA field:field { return field; })*
  { return [first].concat(others) }

name = field

field = ( escaped  / non_escaped )

escaped =
  DQUOTE
  parts:(_2DQUOTE / ESCAPED_TEXTDATA)*
  // Handle EOF by giving non-error if there is no more input
  (!. / DQUOTE)
  { return parts.join(''); }

non_escaped = TEXTDATA

CRLF = "\r" / "\n" / "\r\n"

COMMA = ","

DQUOTE = '"'

_2DQUOTE = '""' { return '"'; }

TEXTDATA = c:[\x20\x21\x23-\x2b\u002d-\uffff]* { return c.join(''); }

ESCAPED_TEXTDATA = c:[\t-\r\x20\x21\u0023-\uffff]+ { return c.join(''); }
