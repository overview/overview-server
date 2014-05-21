# Tracks certain events with Google Analytics and/or Intercom
run = ->
  trackIntercomEvent = (action, metadata) ->
    window.Intercom?('trackEvent', action, metadata || {})
    undefined

  trackGoogleAnalyticsEvent = (category, action, opt_label, opt_value, opt_noninteraction) ->
    # We need to fetch window._gaq in here, not in the outer scope: when GA
    # first loads (asynchronously) it resets window._gaq from a normal array
    # to a funky object.
    window._gaq?.push([ '_trackEvent', category, action, opt_label, opt_value, opt_noninteraction])
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
        when 'confirmation-update' then trackGoogleAnalyticsEvent('Login', 'Confirmed account')
        when 'document-set-delete' then trackGoogleAnalyticsEvent('Document sets', 'Deleted document set')
        when 'password-create' then trackGoogleAnalyticsEvent('Login', 'Requested password reset')
        when 'password-update' then trackGoogleAnalyticsEvent('Login', 'Reset password')
        when 'session-create' then trackGoogleAnalyticsEvent('Login', 'Logged in')
        when 'session-delete' then trackGoogleAnalyticsEvent('Login', 'Logged out')
        when 'user-create' then trackGoogleAnalyticsEvent('Login', 'Registered')

  # Events based on the URL
  #
  # If the user visits a certain URL, we track an event.
  pathname = window.location.pathname
  switch pathname
    when '', '/' then trackGoogleAnalyticsEvent('Navigation', 'Viewed splash')
    when '/documentsets' then trackGoogleAnalyticsEvent('Navigation', 'Viewed document set index')
    when '/help' then trackGoogleAnalyticsEvent('Navigation', 'Viewed help')
    else
      if /^\/documentsets\/\d+$/.test(pathname)
        trackGoogleAnalyticsEvent('Navigation', 'Document set')

  # Events based on the DOM
  if document.getElementsByClassName?('document-set-creation-jobs')?.length
    trackIntercomEvent('created-document-set')
    trackGoogleAnalyticsEvent('Document sets', 'Created document set')

  # Events based on interaction
  #
  # Should these be here, or in another file?
  #
  # We avoid jQuery, to remove a dependency. Luckily, form submit events bubble
  # in modern browsers (not IE8). http://www.quirksmode.org/dom/events/submit.html
  document.addEventListener('submit', (e) ->
    el = e.target
    while el? && el.getAttribute? && (el.getAttribute('id') != 'tree-app-tags' && el.getAttribute('class') != 'vertical-tag-list')
      el = el.parentNode

    if el? && el.getAttribute?
      trackIntercomEvent('created-tag')
      trackGoogleAnalyticsEvent('Document set', 'Created tag')
  , false)

  undefined

load = ->
  document.removeEventListener('DOMContentLoaded', load, false)
  run()
document.addEventListener('DOMContentLoaded', load, false)
