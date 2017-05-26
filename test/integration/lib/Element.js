const Locator = require('./Locator')
const debug = require('debug')('Element')

// A wrapper around a a Selenium WebElement.
module.exports = class Element {
  constructor(driverElement) {
    this.driverElement = driverElement
  }

  // Clicks and returns once the click is done.
  async click() {
    debug('click')
    await this.driverElement.click()
  }

  // Sends keys to the element. Returns when done.
  async sendKeys(keys) {
    debug(`sendKeys(${keys})`)
    await this.driverElement.sendKeys(keys)
  }

  // Returns a Promise of the text in the element.
  async getText() {
    debug('getText()')
    return await this.driverElement.getText()
  }

  // Returns a Promise of the element's attribute.
  async getAttribute(attribute) {
    debug(`getAttribute(${JSON.stringify(attribute)})`)
    return await this.driverElement.getAttribute(attribute)
  }

  // Returns a Promise of the element's selected-ness
  //
  // This will indicate, for instance, whether a checkbox is checked.
  async isSelected() {
    debug('isSelected()')
    return await this.driverElement.isSelected()
  }

  // Schedules a clear of the element. Returns an empty Promise.
  async clear() {
    debug('clear')
    await this.driverElement.clear()
  }
}
