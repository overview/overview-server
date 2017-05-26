const webdriver = require('selenium-webdriver')
const By = webdriver.By

const ValidOptions = {
  class: null,
  className: null,
  contains: null,
  enabled: null,
  id: null,
  index: null,
  name: null,
  tag: null,
  xpath: null,
  css: null,
  link: null,
  button: null,
}

// Turns locator options into a Selenium By.
module.exports = class Locator {
  constructor(items) {
    if (Array.isArray(items)) {
      if (items.length === 0) {
        throw new Error(`Your locator must have length at least 1`)
      }

      if (items.length > 1 && items.some(i => i instanceof String || i.css || i.xpath)) {
        throw new Error(`Your locator nests "css" or "xpath" selectors. Either use a single CSS/XPath selector or use other options when nesting.`)
      }

      this.items = items
    } else {
      this.items = [ items ]
    }
  }

  toBy() {
    if (this.items[0] instanceof String || typeof this.items[0] === 'string') {
      return By.css(this.items[0])
    } else if (this.items[0].css) {
      if (Object.keys(this.items[0]).length > 1) {
        throw new Error(`A "css" locator cannot include any property except "css": ${JSON.stringify(this.items[0])}`)
      }

      return By.css(this.items[0].css)
    } else if (this.items[0].xpath) {
      if (Object.keys(this.items[0]).length > 1) {
        throw new Error(`A "xpath" locator cannot include any property except "xpath": ${JSON.stringify(this.items[0])}`)
      }

      return By.xpath(this.items[0].xpath)
    } else {
      const xpaths = this.items.map((i) => this._itemToXPath(i))
      return By.xpath(xpaths.join(''))
    }
  }

  _itemToXPath(item) {
    const invalidKeys = Object.keys(item).filter(k => !ValidOptions.hasOwnProperty(k))
    if (invalidKeys.length > 0) {
      throw new Error(`Invalid option(s) {invalidKeys.join(', ')} in Locator ${JSON.stringify(item)}`)
    }

    let tag = '*'
    if (item.button) tag = 'button'
    if (item.link) tag = 'a'
    if (item.tag) tag = item.tag

    const attrs = []
    for (let s of [ item.contains, item.link, item.button ]) {
      if (!s) continue
      attrs.push(`contains(., '${s.replace(/'/g, "\\'")}')`)
    }

    for (let s of [ item.className, item.class ]) {
      if (!s) continue
      attrs.push(`contains(concat(' ', @class, ' '), ' ${s} ')`)
    }

    for (let attr of [ 'id', 'name', 'value' ]) {
      if (!item.hasOwnProperty(attr)) continue
      attrs.push(`@${attr}='${item[attr]}'`)
    }

    if (item.hasOwnProperty('index')) {
      attrs.push(`position()=${item.index}`)
    }

    if (item.hasOwnProperty('enabled')) {
      if (item.enabled) {
        attrs.push('not(@disabled)')
      } else {
        attrs.push('@disabled')
      }
    }

    return `//${tag}` + attrs.map(attr => `[${attr}]`).join('')
  }
}
