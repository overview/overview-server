$ ->
  $root = $('#documentcloud-import>.with-login')

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
    deferred = dcimport.request_json_with_login('https://www.documentcloud.org/api/projects.json', $root[0])
    deferred.done(show_data)

  $manual_root = $('#documentcloud-import>.manual')
  $manual_root.find('a:eq(0)').on 'click', (e) ->
    $manual_root.find('form').toggle()
