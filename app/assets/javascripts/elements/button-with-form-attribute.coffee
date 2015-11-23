# Shim any <button form="some-form-id"...></button> to submit the form
define [ 'jquery' ], ($) ->
  browserSupportsFormAttribute = ->
    # Copied from
    # https://github.com/Modernizr/Modernizr/blob/master/feature-detects/forms/formattribute.js
    # (MIT-license)
    form = document.createElement('form')
    input = document.createElement('input')
    div = document.createElement('div')
    id = 'formtest' + (new Date()).getTime()
    bool = false

    form.id = id

    input.setAttribute('form', id)

    div.appendChild(form)
    div.appendChild(input)

    document.body.appendChild(div)

    bool = form.elements && form.elements.length == 1 && input.form == form

    div.parentNode.removeChild(div)

    bool

  $(document).on 'click', 'button[form]', (ev) ->
    if browserSupportsFormAttribute()
      # Do nothing
    else
      formId = ev.currentTarget.getAttribute('form')
      method = ev.currentTarget.getAttribute('type')
      if method not of [ 'submit', 'reset' ]
        throw '<button> type must be "submit" or "reset"'
      $('#' + formId)[type]()
    undefined
