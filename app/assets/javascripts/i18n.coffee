quote = "'"

_handle_unquoted_substring = (s, args) ->
  s = s.replace /\{(\d+)(?:,(\w+))?(?:,([^\}]+))?\}/g, (match, index, format_type, format_style) ->
    if format_type == 'number' && (m = /^(.*)\.(.*)$/.exec(format_style))
      if m[0] != '0' && !(/^0+$/.test(m[2]))
        throw 'We only support "0.00" float formats'
      args[+index].toFixed(m[2].length)
    else
      args[+index]

_handle_string_parts = (s, args) ->
  quote_index = s.indexOf(quote)

  if quote_index == -1
    _handle_unquoted_substring(s, args)
  else
    if quote_index == 0
      if s[1] == "'"
        "'" + _handle_string_parts(s.substring(2), args)
      else
        next_quote_index = s.indexOf(quote, 1)
        s.substring(1, next_quote_index) + _handle_string_parts(s.substring(next_quote_index + 1), args)
    else
      _handle_unquoted_substring(s.substring(0, quote_index), args) + _handle_string_parts(s.substring(quote_index), args)

# Defines a method that translates messages based on keys.
#
# The message formats are described at
# http://docs.oracle.com/javase/6/docs/api/java/text/MessageFormat.html
#
# This implementation is incomplete; see unit tests for a list of supported
# features.
window.use_i18n_messages = (messages) ->
  (key) ->
    args = Array.prototype.slice.call(arguments, 1)
    m = messages[key]
    _handle_string_parts(m, args)
