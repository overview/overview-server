define [
  'jquery',
  'i18n',
  'dcimport/CredentialStore',
  'dcimport/request_json_with_login',
  'dcimport/templates/project',
  'dcimport/templates/split_documents'
], ($, i18n, CredentialStore, request_json_with_login, project_template, split_documents_template) ->
  PROJECT_URL = '/imports/documentcloud' # where to POST

  empty_html = '<p>You have no projects to import.</p>' # FIXME i18n

  (div) ->
    show_data = (data) ->
      $div = $(div)
      $div.empty()

      if !data?.projects?.length
        $div.append(empty_html)
      else
        $checkbox_form = $(split_documents_template({ i18n: i18n }))
        $div.append($checkbox_form)
        credentials = CredentialStore.get()
        $ul = $('<ul class="unstyled projects"></ul>')
        for project in data.projects
          html = project_template({ i18n: i18n, url: PROJECT_URL, project: project, credentials: credentials })
          $ul.append(html)
        $div.append($ul)

    deferred = request_json_with_login('https://www.documentcloud.org/api/projects.json?include_document_ids=false', div)
    deferred.done(show_data)
