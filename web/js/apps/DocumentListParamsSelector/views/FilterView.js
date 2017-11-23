'use strict'

const h = require('escape-html')

const tinycolor = require('tinycolor')

// Displays a small box and dropdown to view and change a group of choices.
//
// There are three groups of data here:
//
// **A. Render options.** These are set during initialization. For instance,
// to render a list of coffees to filter by:
//
//     renderOptions: {
//         iconClass: 'coffee', // icon name from http://fontawesome.io/icons/
//         messages: {
//             placeholder: 'Search by coffees mentioned',
//             choicesEmpty: 'We are unaware of any coffee in any document',
//             // Prompts displayed in the drop-down:
//             selectOneHtml: 'Documents <strong>mentioning</strong> this coffee',
//             selectNotHtml: 'Documents <strong>not mentioning</strong> this coffee',
//             selectAnyHtml: 'Documents mentioning <strong>at least one</strong> of these coffees',
//             selectAllHtml: 'Documents mentioning <strong>all</strong> of these coffees',
//             selectNoneHtml: 'Documents mentioning <strong>none</strong> of these coffees',
//             // Descriptions displayed when collapsed:
//             selectedOneHtml: 'mentioning {0}',
//             selectedNotHtml: '<strong>not</strong> mentioning {0},',
//             selectedAnyHtml: 'mentioning <strong>any</strong> of {0}',
//             selectedAllHtml: 'mentioning <strong>all</strong> of {0}',
//             selectedNoneHtml: 'mentioning <strong>none</strong> of {0}',
//         }
//     }
//
// **B. Choices.** These are set by code when options change. FilterView will
// always display choices in the order specified.
//
//     choices: [
//         { id: '1', name: 'Filter', color: '#ffffff' },
//         { id: '2', name: 'Cappuccino', color: '#b5947d' },
//         { id: '3', name: 'Latte', color: '#6f4e37' },
//         ...
//     ]
//
// **C. Selection.** This is the list of the user's selections. This class
// displays and emits selections when the calling class asks.
//
//     selection: {
//         ids: [ '1', '3' ],
//         operation: 'any', // 'any', 'none', or 'all'; defaults to 'any'
//     }
class FilterView {
  constructor(options) {
    if (!options.renderOptions) throw new Error('Must set options.renderOptions, an Object')
    if (!options.choices) throw new Error('Must set options.choices, an Array')
    if (!options.selection) throw new Error('Must set options.selection, an Object')
    if (!options.onSelect) throw new Error('Must set options.onSelect, a Function accepting a { ids, operation } Object')

    this.renderOptions = options.renderOptions
    this.messages = options.renderOptions.messages
    this.choices = options.choices
    this.selection = options.selection
    this.onSelect = function(selection) { const f = options.onSelect; f(selection) } // do not set "this"

    this._createEl(options)

    this.renderCollapsed()
  }

  setChoices(choices) {
    this.choices = choices
    this.renderCollapsed()
  }

  setSelection(selection) {
    this.selection = selection
    // Don't re-render the expanded view. If we're expanded and the selection
    // changes, that's almost certainly because we're changing it through the
    // form.
    this.renderCollapsed()
  }

  _renderCollapsedChoiceHtml(choice) {
    return [
      '<span class="selected-choice">',
        renderSwatch(choice.color),
        '<span class="name">', h(choice.name), '</span>',
      '</span>',
    ].join('')
  }

  _renderCollapsedChoicesHtmlByIds(ids) {
    return this.choices
      .filter(f => ids.indexOf(f.id) !== -1)
      .map(f => this._renderCollapsedChoiceHtml(f))
      .join('')
  }

  _renderCollapsedHtml() {
    let descriptionHtml
    if (this.selection.ids.length === 0) {
      descriptionHtml = this.messages.placeholder
    } else {
      const idsHtml = this._renderCollapsedChoicesHtmlByIds(this.selection.ids)
      let opName
      if (this.selection.ids.length === 1) {
        opName = this.selection.operation == 'any' ? 'One' : 'Not'
      } else {
        opName = this.selection.operation.replace(/[a-z]/, firstLetter => firstLetter.toUpperCase())
      }
      const messageKey = `selected${opName}Html`
      const message = this.messages[messageKey]
      if (!message) throw new Error(`Missing message: ${messageKey}. Please set it in options.renderOptions.`)
      descriptionHtml = message.replace('{0}', idsHtml)
    }

    return [
      '<a class="description" href="#">',
        '<i class="icon icon-', this.renderOptions.iconClass, '"></i>',
        '<span>', descriptionHtml, '</span>',
      '</a>',
      '<a href="#" class="nix">&times;</a>',
    ].join('')
  }

  _renderExpandedChoiceHtml(choice) {
    const maybeChecked = this.selection.ids.indexOf(choice.id) === -1 ? '' : ' checked' // " checked" or ""
    return [
      '<label>',
        '<span class="checkbox">',
          '<input type="checkbox" name="choice" value="', h(choice.id), '" tabindex="-1"', maybeChecked, '>',
        '</span>',
        renderSwatch(choice.color),
        '<span class="name">', h(choice.name), '</span>',
      '</label>',
    ].join('')
  }

  _renderExpandedChoicesHtml() {
    return [
      '<ul class="choices">',
        this.choices.map(f => `<li>${this._renderExpandedChoiceHtml(f)}</li>`).join(''),
      '</ul>',
    ].join('')
  }

  _renderExpandedOperationsLis() {
    const nIds = this.selection.ids.length
    if (nIds === 0) return ''
    const op = this.selection.operation

    // Only one of "all" or "any" will appear when there are >1 tags to select.
    // We show the one that is already selected, or "any" if "none" is selected.
    const any = {
      messageHtml: nIds === 1 ? this.messages['selectOneHtml'] : this.messages['selectAnyHtml'],
      checked: op === 'any' || (nIds === 1 && op === 'all'),
      enabled: nIds > 1 || op !== 'all',
    }

    const all = {
      messageHtml: nIds === 1 ? this.messages['selectOneHtml'] : this.messages['selectAllHtml'],
      checked: op === 'all' || (nIds === 1 && op === 'any'),
      enabled: nIds > 1 || op === 'all',
    }

    const none = {
      messageHtml: nIds === 1 ? this.messages['selectNotHtml'] : this.messages['selectNoneHtml'],
      checked: op === 'none',
    }

    return [
      '<ul class="operations">',
        '<li>',
          (any.enabled ? [
            '<label>',
              '<span class="radio">',
                '<input type="radio" name="operation" value="any" tabindex="-1"', (any.checked ? ' checked' : ''), '>',
              '</span>',
              '<span class="name">', any.messageHtml, '</span>',
            '</label>',
          ].join('') : ''),
          (all.enabled ? [
            '<label>',
              '<span class="radio">',
                '<input type="radio" name="operation" value="all" tabindex="-1"', (all.checked ? ' checked' : ''), '>',
              '</span>',
              '<span class="name">', all.messageHtml, '</span>',
            '</label>',
          ].join('') : ''),
          '<label>',
            '<span class="radio">',
              '<input type="radio" name="operation" value="none" tabindex="-1"', (none.checked ? ' checked' : ''), '>',
            '</span>',
            '<span class="name">', none.messageHtml, '</span>',
          '</label>',
        '</li>',
      '</ul>',
    ].join('')
  }

  _renderExpandedOperationsHtml() {
    if (this.selection.ids.length === 0) {
      return ''
    } else {
      return `<ul class="operations">${this._renderExpandedOperationsLis()}</ul>`
    }
  }

  _renderExpandedHtml() {
    return [
      '<div class="popup-frame">',
        this._renderExpandedChoicesHtml(),
        this._renderExpandedOperationsHtml(),
      '</div>',
    ].join('')
  }

  // Sets this.el, this.popupEl=null
  _createEl() {
    this.el = document.createElement('div')
    this.el.className = 'document-filter'
    this.el.innerHTML = this._renderCollapsedHtml()
    this.popupEl = null // shows up when expanded
  }

  renderCollapsed() {
    this.el.innerHTML = this._renderCollapsedHtml()
    this.el.classList.toggle('empty', this.selection.ids.length === 0)
  }

  attachEventListeners() {
    this.el.addEventListener('click', ev => {
      ev.preventDefault() // user is clicking a <a>

      if (ev.target.classList.contains('nix')) {
        this.collapse()
        this.buildAndEmitSelection() // no form elements => empty selection
      } else {
        this.expand()
      }
    })
  }

  collapse() {
    if (!this.popupEl) return

    this.el.classList.remove('expanded')
    this.popupEl.parentNode.removeChild(this.popupEl) // kills event listeners
    this.popupEl = null
  }

  expand() {
    this.el.classList.add('expanded')
    this.popupEl = document.createElement('div')
    this.popupEl.className = 'document-filter-popup'
    this.popupEl.innerHTML = this._renderExpandedHtml()

    // popupEl is a <body>-covering <div> (that grabs clicks and solves z-index
    // issues). Inside it is the box we want to put under the search box
    const popupFrame = this.popupEl.childNodes[0]
    const box = this.el.getBoundingClientRect()
    popupFrame.style.left = box.left + 'px'
    popupFrame.style.width = box.width + 'px'
    popupFrame.style.top = (box.top + box.height) + 'px'

    document.body.appendChild(this.popupEl)

    /*
     * Here are the behaviors we want:
     *
     * * User clicks outside the popup: dismiss popup
     * * User clicks a <input>: set selection, leave popup open
     * * User clicks a <label> but not <input>: set selection, dismiss popup
     *
     * When the user clicks a <label>, DOM3 will fire a "click" and "change" and
     * _then_ set the form value. So we perform all actions in a setTimeout. We
     * know that _all_ events related to a click will complete before a
     * setTimeout-scheduled callback runs.
     */

    let timeout = null
    let shouldEmit = false
    let shouldCollapse = false

    const handleEvents = () => {
      timeout = null

      if (shouldEmit) {
        this.buildAndEmitSelection()
        if (!shouldCollapse) this.renderExpandedOperations()
      }
      if (shouldCollapse) this.collapse()

      shouldEmit = false
      shouldCollapse = false
    }

    this.popupEl.addEventListener('change', ev => {
      shouldEmit = true
      if (!timeout) timeout = setTimeout(handleEvents, 0)
    })

    this.popupEl.addEventListener('click', ev => {
      if (ev.target.tagName !== 'INPUT' && !(ev.target.tagName === 'SPAN' && ev.target.className in { checkbox: null, radio: null })) {
        shouldCollapse = true
      }
      if (!timeout) timeout = setTimeout(handleEvents, 0)
    })
  }

  buildAndEmitSelection() {
    let selection = { ids: [], operation: 'any' }
    if (this.popupEl) {
      for (const el of this.popupEl.querySelectorAll('input[name=choice]:checked')) {
        selection.ids.push(el.getAttribute('value'))
      }

      for (const el of this.popupEl.querySelectorAll('input[name=operation]:checked')) {
        selection.operation = el.getAttribute('value')
      }
    }

    this.onSelect(selection)
  }

  renderExpandedOperations() {
    const existingEl = this.popupEl.querySelector('ul.operations')
    const newHtml = this._renderExpandedOperationsLis()
    if (!newHtml) {
      if (existingEl) {
        existingEl.parentNode.removeChild(existingEl)
      }
    } else {
      if (existingEl) {
        existingEl.innerHTML = newHtml
      } else {
        const newEl = document.createElement('ul')
        newEl.className = 'operations'
        newEl.innerHTML = newHtml
        this.popupEl.childNodes[0].appendChild(newEl)
      }
    }
  }
}

function lightOrDarkClass(color) {
  const c = tinycolor.mostReadable(color, ['white', 'black']).toName()
  return c === 'white' ? 'dark' : 'light'
}

function renderSwatch(color) {
  const lightOrDark = lightOrDarkClass(color)
  return `<span class="swatch swatch-${lightOrDark}" style="background-color: ${color}"></span>`
}

module.exports = FilterView
