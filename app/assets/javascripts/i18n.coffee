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
        "#{parseInt(value, 10)}"
      else
        # Doesn't handle currency or percent
        digits = node.format_style.split('.')[1].length
        parseFloat(value).toFixed(digits) # Doesn't handle number format
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
    ast = cache.get_ast(key)
    _walk_ast(ast, args)

  i18n.reset_messages = (messages) ->
    cache.reset(messages)

  i18n
