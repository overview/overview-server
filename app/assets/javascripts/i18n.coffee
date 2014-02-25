# Defines i18n, initialized using "window.messages" (a hash of messages).
#
# Usage:
#
#   window.messages = { 'temp.message': 'foo {0}' }
#   define [ 'i18n' ], (i18n) ->
#     console.log(i18n('temp.message', param1))
#
#     i18n.reset_messages({ 'temp.message': 'bar {0}' })
#
# The message formats are described at
# http://docs.oracle.com/javase/6/docs/api/java/text/MessageFormat.html
#
# This implementation is incomplete; see unit tests for a list of supported
# features.
define [ 'parsers/message_format' ], (MessageFormatParser) ->
  _walk_ast = (ast, args) ->
    # The abstract syntax of a tree looks like this:
    #
    # [
    #    "You've saved ",
    #    {
    #       "index": 0,
    #       "format_type": "choice",
    #       "format_style": [
    #          {
    #             "limit": 0,
    #             "format": [
    #                "nothing"
    #             ]
    #          },
    #          "|",
    #          {
    #             "limit": 1,
    #             "format": [
    #                "one file"
    #             ]
    #          },
    #          "|",
    #          {
    #             "limit": 1,
    #             "format": [
    #                "some ",
    #                {
    #                   "index": 0,
    #                   "format_type": "number",
    #                   "format_style": "integer"
    #                },
    #                " files"
    #             ]
    #          }
    #       ]
    #    }
    # ]
    #
    # All we have to do is (recursively) convert objects into strings, then
    # join the strings.

    _walk_ast_node(ast, args)

  toString = Object.prototype.toString

  # Adds commas to the string so they're grouped by three at the right. For
  # instance, "00000000" becomes "00,000,000".
  addCommas = (s) ->
    ret = ""
    while m = /(.*)(\w)(\w{3})\b/.exec(s)
      s = m[1] + m[2]
      ret = if ret.length
        "#{m[3]},#{ret}"
      else
        m[3]
    if ret.length
      "#{s},#{ret}"
    else
      s

  # intToString(1234.234) => "1,234"
  intToString = (i) ->
    s = "#{parseInt("#{i}", 10)}"
    addCommas(s)

  # floatToString(112412.1241234, 2) => "112,412.12"
  floatToString = (f, decimalDigits) ->
    s = parseFloat(f).toFixed(decimalDigits)
    parts = s.split('.')
    whole = parts[0]
    decimal = parts[1] && ".#{parts[1]}" || ""
    addCommas(whole) + decimal

  _isString = (obj) ->
    toString.call(obj) == '[object String]'

  _isArray = (obj) ->
    toString.call(obj) == '[object Array]'

  _walk_ast_node = (node, args) ->
    if _isString(node)
      node
    else if _isArray(node)
      (_walk_ast_node(subnode, args) for subnode in node).join('')
    else
      _ast_node_to_string(node, args)

  _ast_node_to_string = (node, args) ->
    value = args[node.index]
    if node.format_type == 'number'
      node.format_style ||= 'integer'
      if node.format_style == 'integer'
        intToString(parseInt(value, 10))
      else
        # Doesn't handle currency or percent
        digits = node.format_style.split('.')[1].length
        floatToString(parseFloat(value), digits) # doesn't handle number format
    else if node.format_type == 'date'
      if value
        # assume format_style == 'medium'
        [
          value.getFullYear()
          (value.getMonth() + 101).toString(10).substring(1)
          (value.getDate() + 100).toString(10).substring(1)
        ].join('-')
      else
        ''
    else if node.format_type == 'time'
      if value
        # assume format_style == 'short'
        [
          (value.getHours() + 100).toString(10).substring(1)
          (value.getMinutes() + 100).toString(10).substring(1)
        ].join(':')
      else
        ''
    else if node.format_type == 'choice'
      _choice_to_string(node.format_style, parseFloat(value), args)
    else
      "#{value}"

  _choice_to_string = (choices, number, args) ->
    for choice in choices
      if number <= choice.limit
        return _walk_ast_node(choice.format, args)
    return _walk_ast_node(choices[choices.length - 1].format, args)

  class MessageCache
    constructor: (messages) ->
      @reset(messages)

    reset: (messages) ->
      @asts = {}
      @messages = {}
      @messages[k] = v for k, v of messages
      this

    get_ast: (key) ->
      @asts[key] ||= if @messages[key]
        MessageFormatParser.parse(@messages[key])
      else
        throw "Unknown i18n message key '#{key}'"

  cache = new MessageCache(window.messages || {})

  i18n = (key, args...) ->
    i18n.translate.apply(this, arguments)

  i18n.translate = (key, args...) ->
    ast = cache.get_ast(key)
    _walk_ast(ast, args)

  i18n.reset_messages = (messages) ->
    cache.reset(messages)

  i18n.namespaced = (namespace) ->
    (key, args...) -> i18n("#{namespace}.#{key}", args...)

  i18n
