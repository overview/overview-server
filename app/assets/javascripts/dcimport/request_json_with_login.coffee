define [ 'jquery', 'base64', 'i18n', 'dcimport/CredentialStore', 'dcimport/templates/login' ], ($, Base64, i18n, CredentialStore, login_template) ->
  Deferred = $.Deferred

  loading_html = '<p>Loading...</p>' # FIXME translate

  credentials_to_authorization_header = (credentials) ->
    "Basic #{Base64.encode64("#{credentials.email}:#{credentials.password}")}"

  prompt_for_credentials = (div, is_error) ->
    deferred = new Deferred()

    $div = $(div)
    $div.empty()
    $login_form = $(login_template({ i18n: i18n, error: is_error || false }))
    $div.append($login_form)

    $login_form.one 'submit', (e) ->
      e.preventDefault()

      credentials = {
        email: $login_form.find('input[name=dcimport_email]').val()
        password: $login_form.find('input[name=dcimport_password]').val()
      }

      $div.empty()

      deferred.resolve(credentials)

    deferred

  request_json_with_login_recursive = (deferred, url, prompt_div, attempt) ->
    attempt ||= 0
    recurse = (is_error) ->
      d = prompt_for_credentials(prompt_div, is_error)
      d.done (credentials) ->
        CredentialStore.store(credentials)
        request_json_with_login_recursive(deferred, url, prompt_div, attempt + 1)

    credentials = CredentialStore.get()

    if credentials?
      $div = $(prompt_div)
      $div.empty()
      $div.append(loading_html)

      ajax_options = if $.support.cors
        {
          # Request from DocumentCloud directly
          type: 'GET'
          url: url
          timeout: 20000
          dataType: 'json'
          beforeSend: (xhr) ->
            xhr.setRequestHeader('Authorization', credentials_to_authorization_header(credentials))
        }
      else
        {
          # Request through our own proxy: /documentcloud-proxy/* instead of DC's /api/*
          type: 'POST'
          url: url.replace(/^.*\/api\//, '/documentcloud-proxy/')
          timeout: 20000
          dataType: 'json'
          data: $.extend({}, window.csrfTokenData || {}, { email: credentials.email, password: credentials.password })
        }

      ajax = $.ajax(ajax_options)
      ajax.fail(-> recurse(true))
      ajax.done((json) -> deferred.resolve(json))
    else
      recurse(false)

    undefined

  (url, prompt_div) ->
    deferred = new Deferred()
    request_json_with_login_recursive(deferred, url, prompt_div)
    deferred
