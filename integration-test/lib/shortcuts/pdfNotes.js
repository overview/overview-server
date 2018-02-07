'use strict'

class PdfNotesShortcuts {
  constructor(browser) {
    this.browser = browser
  }

  async createNote(text) {
    const b = this.browser

    await b.inFrame('document-contents', async () => {
      await b.assertExists({ css: '#viewer .textLayer', wait: 'pageLoad' }) // debugging: Jenkins is failing to find '#viewer .textLayer div'
      await b.assertExists({ css: '#viewer .textLayer div', wait: 'pageLoad' })
      await b.waitUntilBlockReturnsTrue('notes code is loaded', 'pageLoad', function() {
        return document.querySelector('.noteLayer') !== null // it's invisible: we can't assertExists()
      });

      // Often, even after the notes code is loaded, clicking button#addNote
      // doesn't accomplish anything. [adam, 2017-12-08] I suspect that's
      // because the _toolbar_ code isn't loaded yet, but I'm not going to delve
      // into pdfjs to be sure. So let's just assume it's on its way; there are
      // no HTTP requests remaining.
      await b.sleep(1000)

      await b.click('button#addNote')
      await b.assertExists({ css: '#viewerContainer.addingNote', wait: 'fast' }) // wait for it to listen to mouse events

      // [adamhooper, 2018-01-02] TODO does this sleep() call help? We were getting
      // errors with the `#viewer .noteLayer section` selector earlier: sometimes it
      // wasn't appearing. If this line makes those errors go away, that means there's
      // a race and we're failing to wait for something -- but I don't know what.
      await b.sleep(1000)

      const el = (await b.find({ css: '#viewer .textLayer div' })).driverElement
      await b.driver.actions()
        .mouseDown(el)
        .mouseMove({ x: 200, y: 100 })
        .mouseUp()
        .perform()

      await b.assertExists({ css: '#viewer .noteLayer section', wait: true })
      await b.assertExists({ css: '.editNoteTool', wait: true })
      await b.sendKeys(text || 'Hello, world!', '.editNoteTool textarea')
      await b.click('.editNoteTool button.editNoteSave')
      await b.assertExists('.editNoteTool button.editNoteSave[disabled]', { wait: true })
      await b.click('.editNoteTool button.editNoteClose')
    })
  }
}

// Shortcuts for doing stuff while on the DocumentSet page.
//
// Assumptions shared by all methods:
//
// * You have navigated to a PDF document.
module.exports = function(browser) {
  return new PdfNotesShortcuts(browser)
}
