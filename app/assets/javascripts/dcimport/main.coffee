$ ->
  $root = $('#documentcloud-import')

  # FIXME translate
  dcimport.templates.loading = _.template("""<div class="loading">Loading...</div>""")
  dcimport.templates.empty = _.template("""<p>You have no projects to import.</p>""")

  prompt_for_new_credentials = (callback) ->
    $root.empty()
    $login_form = $(dcimport.templates.login())
    $root.append($login_form)
    $login_form.one 'submit', (e) ->
      e.preventDefault()

      email = $login_form.find('input[name=dcimport_email]').val()
      password = $login_form.find('input[name=dcimport_password]').val()

      sessionStorage.setItem('dcimport_email', email)
      sessionStorage.setItem('dcimport_password', password)

      callback.apply({})

  ajax_with_login_retry = (path, callback) ->
    recurse = () -> prompt_for_new_credentials -> ajax_with_login_retry(path, callback)

    email = sessionStorage.getItem('dcimport_email')
    password = sessionStorage.getItem('dcimport_password')

    if email && password
      $root.empty()
      $root.append(dcimport.templates.loading())

      $.ajax({
        url: "https://www.documentcloud.org/api#{path}?callback=?",
        username: email,
        password: password,
        dataType: 'jsonp',
      }).fail((xhr, error_type) ->
        recurse if xhr.status == 403
      ).done((data) -> callback(data))
    else
      recurse()

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

    ajax_with_login_retry '/projects.json', (data) ->
      show_data(data)
