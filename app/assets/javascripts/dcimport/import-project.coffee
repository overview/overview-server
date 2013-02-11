$ = jQuery
#
# FIXME translate
dcimport.templates.empty = _.template("""<p>You have no projects to import.</p>""")

dcimport.import_project_with_login = (div) ->
  show_data = (data) ->
    $div = $(div)
    $div.empty()

    if !data?.projects?.length
      $div.append(dcimport.templates.empty())
    else
      credentials = dcimport.get_credentials()
      $ul = $('<ul class="unstyled projects"></ul>')
      $ul.append(dcimport.templates._project(project, credentials)) for project in data.projects
      $div.append($ul)

  deferred = dcimport.request_json_with_login('https://www.documentcloud.org/api/projects.json', div)
  deferred.done(show_data)
