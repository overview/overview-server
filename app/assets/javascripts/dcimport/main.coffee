$ ->
  $root = $('#documentcloud-import>.with-login')

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

      deferred = $.ajax({
        url: "https://www.documentcloud.org/api/projects.json",
        timeout: 20000,
        beforeSend: (xhr) ->
          xhr.setRequestHeader('Authorization', "Basic #{Base64.encode64("#{email}:#{password}")}")
      })
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
