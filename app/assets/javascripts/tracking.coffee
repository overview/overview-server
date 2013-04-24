# Tracks certain events with Google Analytics
run = ->
  return if !window._gaq? # This shouldn't happen.

  trackEvent = (category, action, opt_label, opt_value, opt_noninteraction) ->
    # We need to fetch window._gaq in here, not in the outer scope: when GA
    # first loads (asynchronously) it resets window._gaq from a normal array
    # to a funky object.
    window._gaq.push([ '_trackEvent', category, action, opt_label, opt_value, opt_noninteraction])
    undefined

  # Events based on flash.
  #
  # If the controller injects a flash message into the page
  # (in Play, Redirect(...).flashing("event" -> "controller-action")),
  # we track an event.
  #
  # These come before the "Events based on the URL" because that follows web
  # logic: 1) the user clicked something; 2) the controller set the flash and
  # 3) the user was redirected here.
  if flash = document.getElementById('flash')
    for div in flash.childNodes when div.getAttribute?('data-key') == 'event'
      event = div.getAttribute('data-value')
      switch event
        when 'confirmation-update' then trackEvent('Login', 'Confirmed account')
        when 'document-set-create' then trackEvent('Document sets', 'Created document set')
        when 'document-set-create-clone' then trackEvent('Document sets', 'Cloned document set')
        when 'document-set-delete' then trackEvent('Document sets', 'Deleted document set')
        when 'password-create' then trackEvent('Login', 'Requested password reset')
        when 'password-update' then trackEvent('Login', 'Reset password')
        when 'session-create' then trackEvent('Login', 'Logged in')
        when 'session-delete' then trackEvent('Login', 'Logged out')
        when 'user-create' then trackEvent('Login', 'Registered')

  # Events based on the URL
  #
  # If the user visits a certain URL, we track an event.
  pathname = window.location.pathname
  switch pathname
    when '', '/' then trackEvent('Navigation', 'Viewed splash')
    when '/documentsets' then trackEvent('Navigation', 'Viewed document set index')
    when '/help' then trackEvent('Navigation', 'Viewed help')
    else
      if /^\/documentsets\/\d+$/.test(pathname)
        trackEvent('Navigation', 'Document set')

  # Events based on interaction
  #
  # Should these be here, or in another file?
  #
  # We avoid jQuery, to remove a dependency.
  tag_list = document.getElementById('tag-list')
  # Submit events bubble in everything but IE8-
  # http://www.quirksmode.org/dom/events/submit.html
  tag_list?.addEventListener('submit', ->
    trackEvent('Document set', 'Created tag')
  , false)

  undefined

load = ->
  document.removeEventListener('DOMContentLoaded', load, false)
  run()
document.addEventListener('DOMContentLoaded', load, false)
