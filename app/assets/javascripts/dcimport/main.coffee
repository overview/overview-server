class ProjectsFetcher
  constructor: () ->
    if Worker?
      @worker = new Worker('/assets/javascripts/dcimport/login-worker.js')
      @last_request_id = 0
      @callbacks = {}

      @worker.onmessage = (e) =>
        request_id = e.data.request_id
        if @callbacks[request_id]?
          @callback = @callbacks[request_id]
          delete @callbacks[request_id]
          @callback(e.data)

  try_authenticated_fetch: (email, password) ->
    if Worker?
      # Mozilla/Chrome *can* put username/password in the URL. However, if the
      # server returns a 401 response, they will prompt for a password.
      # DocumentCloud currently doesn't (it returns 403), but who's to say
      # that won't change? Also, Mozilla will prompt to user to save the
      # username/password if we do such a JSONP request, and that's
      # confusing. Using a web worker avoids both problems.
      #
      # see https://bugzilla.mozilla.org/show_bug.cgi?id=282547
      d = new $.Deferred()
      request_id = "#{@last_request_id += 1}"
      @callbacks[request_id] = (data) ->
        if data.error?
          d.reject()
        else
          d.resolve(data.data)
      @worker.postMessage({ request_id: request_id, username: email, password: password })
      d
    else
      # IE9
      $.ajax({
        url: "https://www.documentcloud.org/api/projects.json?callback=?",
        username: email,
        password: password,
        dataType: 'jsonp',
        timeout: 20000
      })

$ ->
  $root = $('#documentcloud-import>.with-login')
  projects_fetcher = undefined

  # FIXME translate
  dcimport.templates.loading = _.template("""<div class="loading">Loading...</div>""")
  dcimport.templates.empty = _.template("""<p>You have no projects to import.</p>""")

  prompt_for_new_credentials = (error, callback) ->
    $root.empty()
    $login_form = $(dcimport.templates.login(error))
    $root.append($login_form)
    $login_form.one 'submit', (e) ->
      e.preventDefault()

      email = $login_form.find('input[name=dcimport_email]').val()
      password = $login_form.find('input[name=dcimport_password]').val()

      sessionStorage.setItem('dcimport_email', email)
      sessionStorage.setItem('dcimport_password', password)

      callback.apply({})

  ajax_with_login_retry = (callback) ->
    recurse = (error) -> prompt_for_new_credentials(error, -> ajax_with_login_retry(callback))

    email = sessionStorage.getItem('dcimport_email')
    password = sessionStorage.getItem('dcimport_password')

    if email && password
      $root.empty()
      $root.append(dcimport.templates.loading())

      projects_fetcher ||= new ProjectsFetcher()
      deferred = projects_fetcher.try_authenticated_fetch(email, password)
      deferred.fail(-> recurse(true))
      deferred.done(callback)
    else
      recurse(false)

  show_data = (data) ->
    $root.empty()

    if !data?.projects?.length
      $root.append(dcimport.templates.empty())
    else
      $ul = $('<ul class="unstyled projects"></ul>')
      credentials = {
        username: sessionStorage.getItem('dcimport_email'),
        password: sessionStorage.getItem('dcimport_password'),
      }
      $ul.append(dcimport.templates._project(project, credentials)) for project in data.projects
      $root.append($ul)

  $root.on 'click', 'a', (e) ->
    e.preventDefault()

    ajax_with_login_retry(show_data)

  $manual_root = $('#documentcloud-import>.manual')
  $manual_root.find('a:eq(0)').on 'click', (e) ->
    $manual_root.find('form').toggle()
