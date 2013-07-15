# https://github.com/overview/overview-server/issues/562
window.addEventListener 'beforeunload', ->
  for form in document.getElementsByTagName('form')
    for element in form.elements
      element.setAttribute('disabled', 'disabled')
  undefined
