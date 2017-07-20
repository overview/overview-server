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

CRLF = "\r" / "\n" / "\r\n" / "\u0085"

COMMA = ","

DQUOTE = '"'

_2DQUOTE = '""' { return '"'; }

TEXTDATA = c:[^",\r\n\u0085]* { return c.join('').replace(/[\x00\ufffe\uffff]+/g, '').replace(/[\x01-\x08\x0b\x0c\x0e-\x1f\x7f-\x84\x86-\x9f]+/g, ' '); }

ESCAPED_TEXTDATA = c:[^"]+ { return c.join('').replace(/[\x00\ufffe\uffff]+/g, '').replace(/[\x01-\x08\x0b\x0c\x0e-\x1f\x7f-\x84\x86-\x9f]+/g, ' '); }
