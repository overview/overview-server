const Backbone = require('backbone')

const h = require('escape-html')

function renderLink(link) {
  return [
    '<a href="#" data-url="', h(link.url), '" title="', h(link.title), '">',
      '<i class="icon icon-', h(link.iconClass), '"></i>',
      h(link.text),
      '<span class="caret"></span>',
    '</a>',
  ].join('')
}

module.exports = class DocumentDetailLinksView extends Backbone.View.extend({
  tagName: 'ul',
  events: {
    'click a': '_onClickA'
  },
}) {
  initialize(options) {
    if (!options.views) throw new Error("Must pass options.views, a Backbone.Collection of Views")

    this.views = options.views
    this.listenTo(this.views, 'add remove reset change:documentDetailLink', this.render)

    this.documentId = null
    this.openDiv = null

    this.render()
  }

  remove() {
    if (this.openDiv) {
      document.body.removeChild(this.openDiv)
      this.openDiv = null
    }
    Backbone.View.prototype.remove.call(this)
  }

  _addLi(cid, link) {
    // TODO handle options.at (the insertion index)
    if (this.usedUrls.hasOwnProperty(link.url)) {
      this.usedUrls[link.url].push(cid)
    } else {
      this.usedUrls[link.url] = [ cid ]
      const li = document.createElement('li')
      li.innerHTML = renderLink(link)
      this.el.appendChild(li)
    }
  }

  render() {
    this.usedUrls = {} // url => Array of View IDs

    this.el.innerHTML = ''
    this.views
      .map(view => ({ cid: view.cid, link: view.get('documentDetailLink') }))
      .filter(({ link }) => !!link)
      .forEach(({ cid, link }) => this._addLi(cid, link))
  }

  setDocumentId(documentId) {
    this.documentId = documentId
  }

  _onClickA(ev) {
    ev.preventDefault()

    if (!this.documentId) return // the user clicked this link before a documentId was set; animating away, perhaps?
    if (this.openDiv) return // should never happen

    const a = ev.currentTarget
    const url = a.getAttribute('data-url')

    const cid = this.usedUrls[url][0]
    const view = this.views.get(cid)

    const fullUrl = new URL(url.replace(':documentId', this.documentId))
    fullUrl.searchParams.set('apiToken', view.get('apiToken'))
    fullUrl.searchParams.set('server', view.get('serverUrlFromPlugin') || document.location.origin)

    const div = this.openDiv = document.createElement('div')
    div.className = 'document-detail-popup-container'
    div.innerHTML = [
      '<div class="document-detail-background">',
        '<div class="document-detail-popup">',
          '<iframe id="view-document-detail" src="', h(fullUrl.toString()), '"></iframe>',
        '</div>',
      '</div>',
    ].join('')

    const positioningElement = a.offsetParent && a.offsetParent.querySelector('article') || a
    const articleBounds = positioningElement.getBoundingClientRect()
    const background = div.childNodes[0]
    background.style.left = articleBounds.left + 'px';
    background.style.width = articleBounds.width + 'px';
    background.style.top = articleBounds.top + 'px';
    background.style.height = articleBounds.height + 'px';

    document.body.appendChild(div)

    // If the user clicks in the iframe, we won't see the event. But we _will_
    // see when the user clicks anything else. And this div of ours will stretch
    // across the entire page, so any non-iframe click will hit it.
    div.addEventListener('click', () => {
      document.body.removeChild(div)
      this.openDiv = null
    })
  }
}
