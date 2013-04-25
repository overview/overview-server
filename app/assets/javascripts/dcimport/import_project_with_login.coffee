define [
  'jquery',
  'i18n',
  'dcimport/CredentialStore',
  'dcimport/request_json_with_login',
  'dcimport/templates/project'
], ($, i18n, CredentialStore, request_json_with_login, project_template) ->
  PROJECT_URL = '/documentsets' # where to POST

  empty_html = '<p>You have no projects to import.</p>' # FIXME i18n

  (div) ->
    show_data = (data) ->
      $div = $(div)
      $div.empty()

      if !data?.projects?.length
        $div.append(empty_html)
      else
        $checkbox_form = $('<form method="post" class="update form-inline" action="#split-document"><input id="set-split-documents" type="checkbox" name="split-documents" value="false" /><label for="set-split-documents">Split Documents by Page. Clear?</label></form>')
        $div.append($checkbox_form)
        credentials = CredentialStore.get()
        $ul = $('<ul class="unstyled projects"></ul>')
        for project in data.projects
          html = project_template({ i18n: i18n, url: PROJECT_URL, project: project, credentials: credentials })
          $ul.append(html)
        $div.append($ul)

    deferred = request_json_with_login('https://www.documentcloud.org/api/projects.json', div)
    deferred.done(show_data)
