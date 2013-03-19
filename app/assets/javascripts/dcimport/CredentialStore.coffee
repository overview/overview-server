define [], () ->
  # Keys in sessionStorage
  KEY_EMAIL = 'dcimport_email'
  KEY_PASSWORD = 'dcimport_password'

  {
    get: ->
      ret = {
        email: sessionStorage.getItem(KEY_EMAIL)
        password: sessionStorage.getItem(KEY_PASSWORD)
      }
      if ret.email && ret.password
        ret
      else
        undefined

    store: (credentials) ->
      sessionStorage.setItem(KEY_EMAIL, credentials.email)
      sessionStorage.setItem(KEY_PASSWORD, credentials.password)
  }
