define [
  'jquery',
  'underscore',
  'i18n',
  'dcimport/CredentialStore',
  'dcimport/request_json_with_login',
  'dcimport/templates/project'
], ($, _, i18n, CredentialStore, request_json_with_login, project_template) ->
  PROJECT_URL = '/imports/documentcloud' # where to POST

  empty_html = _.template("""
    <p class="empty"><%- i18n('views.DocumentSet._dcimport.empty') %></p>
  """)

  (div) ->
    $div = $(div)

    show_data = (data) ->
      $div.empty()

      if !data?.projects?.length
        $div.append(empty_html({ i18n: i18n }))
      else
        credentials = CredentialStore.get()
        $ul = $('<ul class="unstyled projects"></ul>')
        for project in data.projects
          html = project_template({ i18n: i18n, url: PROJECT_URL, project: project, credentials: credentials })
          $ul.append(html)
        $div.append($ul)

    deferred = request_json_with_login('https://www.documentcloud.org/api/projects.json?include_document_ids=false', div)
    deferred.done(show_data)
