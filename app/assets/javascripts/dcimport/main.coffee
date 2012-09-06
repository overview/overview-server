$ ->
  $root = $('#documentcloud-import>.with-login')
  worker = undefined # only needed for Mozilla -- see https://bugzilla.mozilla.org/show_bug.cgi?id=282547
  worker_request_id = 0
  worker_messages = {}

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

    wrapped_callback = (data) ->
      if data?.projects[0]?.id == 21 && data?.projects[0]?.title == 'DocumentCloud' && data?.projects[0]?.description == null
        # FIXME DocumentCloud doesn't return a 401 error, as it should.
        # Instead, it lists public documents. If we reach here, we're
        # likely using the public DocumentCloud server and that bug is
        # showing up. So let's treat this as an invalid password.
        #
        # https://github.com/documentcloud/documentcloud/issues/29
        recurse(true)
      else
        callback(data)

    email = sessionStorage.getItem('dcimport_email')
    password = sessionStorage.getItem('dcimport_password')

    if email && password
      $root.empty()
      $root.append(dcimport.templates.loading())

      if $.browser.mozilla
        # Mozilla *can* put username/password in the URL. However, if the
        # server returns a 401 response, it will prompt for a password.
        # DocumentCloud currently doesn't (it returns 403), but who's to say
        # that won't change? Also, Mozilla will prompt to user to save the
        # username/password if we do such a JSONP request, and that's
        # confusing. Using a web worker avoids both problems.
        request_id = "#{worker_request_id += 1}"
        worker_messages[request_id] = wrapped_callback
        worker.postMessage({ request_id: request_id, username: email, password: password })
        window.setTimeout(->
          if worker_messages[request_id]?
            delete worker_messages[request_id]
            recurse(true)
        , 20000)
      else
        $.ajax({
          url: "https://www.documentcloud.org/api/projects.json?callback=?",
          username: email,
          password: password,
          dataType: 'jsonp',
          timeout: 20000,
        }).fail(-> recurse(true) # This is JSONP, so we don't know what the error was
        ).done(wrapped_callback)
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
    if $.browser.mozilla
      worker = new Worker('/assets/javascripts/dcimport/login-worker.js')
      worker.onmessage = (e) ->
        request_id = e.data.request_id
        if worker_messages[request_id]?
          callback = worker_messages[request_id]
          delete worker_messages[request_id]
          callback(e.data.data)

    e.preventDefault()

    ajax_with_login_retry(show_data)

  $manual_root = $('#documentcloud-import>.manual')
  $manual_root.find('a:eq(0)').on 'click', (e) ->
    $manual_root.find('form').toggle()
