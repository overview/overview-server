initialized = false
_style = undefined
_add_rule_impl = undefined

get_style = () ->
  return _style if _style?
  _style = $('<style type="text/css"></style>').appendTo('head')[0]

get_add_rule_impl = () ->
  return _add_rule_impl if _add_rule_impl?
  style = get_style()
  sheet = style.sheet || style.styleSheet
  sheet_rules = sheet.rules || sheet.cssRules
  _add_rule_impl = if sheet.insertRule?
    (selector, rules) ->
      sheet.insertRule("#{selector} { #{rules} }", sheet_rules.length)
  else
    (selector, rules) ->
      sheet.addRule(selector, rules, rules.length)

add_rule = (selector, rules) ->
  get_add_rule_impl()(selector, rules)

colors = [
  { h: 0, s: 1, l: 0.5 },
  { h: 120, s: 1, l: 0.5 },
  { h: 240, s: 1, l: 0.5 }
]

add_color = (index, color) ->
  hsl = "hsl(#{color.h},100%,50%)"
  hsl_gradient = "hsl(#{color.h},100%,85%)"

  add_rule(".tag-color-#{index}", "color: white; background-color: #{hsl};")

  btn_selector=".btn-color-#{index}"
  add_rule(btn_selector,
    """
    color: white;
    text-shadow: 0 0 2px rgba(0, 0, 0, 0.7);
    background-color: #{hsl};
    background-image: -ms-linear-gradient(top, #{hsl_gradient}, #{hsl});
    background-image: -webkit-linear-gradient(top, #{hsl_gradient}, #{hsl});
    background-image: -o-linear-gradient(top, #{hsl_gradient}, #{hsl});
    background-image: -moz-linear-gradient(top, #{hsl_gradient}, #{hsl});
    background-image: linear-gradient(top, #{hsl_gradient}, #{hsl});
    background-repeat: repeat-x;
    border-color: rgba(0, 0, 0, 0.1) rgba(0, 0, 0, 0.1) rgba(0, 0, 0, 0.25);
    """)

  add_rule("#{btn_selector}:hover, #{btn_selector}:active, #{btn_selector}.active, #{btn_selector}.disabled, #{btn_selector}[disabled]",
    """
    background-color: #{hsl_gradient};
    """)

grow_colors = () ->
  new1 = colors.length
  new2 = new1 * 2

  for i in [new1...new2]
    old1 = i - new1
    old2 = old1 + 1
    h1 = colors[old1].h
    h2 = colors[old2].h
    h2 += 360 if h2 < h1
    h = (h1 + h2) / 2
    colors.push({ h: h, s: 1, l: 0.5 })
    add_color(i, colors[i])

class ColorTable
  reserve: (n) ->
    if !initialized
      add_color(0, colors[0])
      add_color(1, colors[1])
      add_color(2, colors[2])
      initialized = true

    while n >= colors.length
      grow_colors()

  get: (n) ->
    this.reserve(n)
    colors[n]

exports = require.make_export_object('views/color_table')
exports.ColorTable = ColorTable
