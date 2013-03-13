# Tracks certain events with Google Analytics
run = ->
  gaq = window._gaq || []

  return if !gaq?

  trackEvent = (category, action, opt_label, opt_value, opt_noninteraction) ->
    gaq.push([ '_trackEvent', category, action, opt_label, opt_value, opt_noninteraction])
    undefined

  # Events based on the URL
  #
  # If the user visits a certain URL, we track an event.
  pathname = window.location.pathname
  switch pathname
    when '', '/' then trackEvent('Navigation', 'Splash')
    when '/documentsets' then trackEvent('Navigation', 'Document set index')
    when '/help' then trackEvent('Navigation', 'Help')
    else
      if /^\/documentsets\/\d+$/.test(pathname)
        trackEvent('Navigation', 'Document set')

  # Events based on flash.
  #
  # If the controller injects a flash message into the page
  # (in Play, Redirect(...).flashing("event" -> "controller-action")),
  # we track an event.
  if flash = document.getElementById('flash')
    for div in flash.childNodes when div.getAttribute?('data-key') == 'event'
      event = div.getAttribute('data-value')
      switch event
        when 'confirmation-update' then trackEvent('Login', 'Confirmed account')
        when 'document-set-create' then trackEvent('Document sets', 'Created')
        when 'document-set-create-clone' then trackEvent('Document sets', 'Cloned')
        when 'document-set-delete' then trackEvent('Document sets', 'Deleted')
        when 'password-create' then trackEvent('Login', 'Requested password reset')
        when 'password-update' then trackEvent('Login', 'Reset password')
        when 'session-create' then trackEvent('Login', 'Logged in')
        when 'session-delete' then trackEvent('Login', 'Logged out')
        when 'user-create' then trackEvent('Login', 'Registered')

  # Events based on interaction
  #
  # Should these be here, or in another file?
  #
  # We avoid jQuery, to remove a dependency.
  tag_list = document.getElementById('tag-list')
  if tag_list
    for form in tag_list.getElementsByTagName('form')
      form.addEventListener('submit', ->
        trackEvent('Document set', 'Create tag')
      , false)

  undefined

load = ->
  document.removeEventListener('DOMContentLoaded', load, false)
  run()
document.addEventListener('DOMContentLoaded', load, false)
