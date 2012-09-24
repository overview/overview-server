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

_walk_ast_node = (node, args) ->
  if _.isString(node)
    node
  else if _.isArray(node)
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
    message = messages[key]

    ast = message_format_parser.parse(message)
    _walk_ast(ast, args)
