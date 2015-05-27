webdriver = require('selenium-webdriver')
By = webdriver.By

ValidOptions =
  class: null
  className: null
  contains: null
  enabled: null
  id: null
  index: null
  name: null
  tag: null
  xpath: null
  css: null
  link: null
  button: null

# Turns locator options into a Selenium By.
module.exports = class Locator
  constructor: (@items) ->
    @items = [ @items ] unless @items instanceof Array

  toBy: ->
    if @items[0].css?
      By.css(@items[0].css)
    else if @items[0].xpath?
      By.xpath(@items[0].xpath)
    else
      xpaths = (@_itemToXPath(item) for item in @items)
      By.xpath(xpaths.join(''))

  _itemToXPath: (item, allowCss) ->
    for k, __ of item when k not of ValidOptions
      throw "Invalid option #{k} in Locator #{JSON.stringify(item)}"

    tag = '*'
    tag = 'button' if item.button?
    tag = 'a' if item.link?
    tag = item.tag if item.tag?

    attrs = []
    for s in [ item.contains, item.link, item.button ] when s?
      attrs.push("contains(., '#{s.replace(/'/g, "\\'")}')")

    for s in [ item.className, item.class ] when s?
      attrs.push("contains(concat(' ', @class, ' '), ' #{s} ')")

    for attr in [ 'id', 'name', 'value' ] when attr of item
      attrs.push("@#{attr}='#{item[attr]}'")

    if item.index?
      attrs.push("position()=#{item.index}")

    if item.enabled?
      if item.enabled
        attrs.push('not(@disabled)')
      else
        attrs.push('@disabled')

    xpath = "//#{tag}"
    for attr in attrs
      xpath += "[#{attr}]"
    xpath
