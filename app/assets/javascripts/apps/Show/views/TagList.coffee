define [ 'jquery', 'underscore', 'backbone', 'i18n', 'spectrum' ], ($, _, Backbone, i18n) ->
  t = i18n.namespaced('views.Tree.show.tag_list')

  addSpectrum = ($els) ->
    $els.filter(':not(.spectrum)').each ->
      $input = $(this)
      $input
        .addClass('spectrum')
        .spectrum({
          preferredFormat: 'hex6'
          showButtons: false
          move: (color) -> $input.spectrum('set', color.toHexString()).change() # Issue #168
        })

  removeSpectrum = ($els) ->
    $els.filter('.spectrum').spectrum('destroy')

  updateSpectrum = ($els) ->
    $els.filter('.spectrum').each ->
      $(this).spectrum('set', $(this).val())

  # Represents a list of tags
  #
  # Usage:
  #   tags = new Tags([])
  #   view = new TagList({ collection: tags })
  #   $('body').append(view.el)
  #
  # The view will emit the following events:
  #
  # * add: user requested to add a new Tag (plain-old-data object with name/color)
  # * remove: user requested to remove a Tag (Backbone.Model)
  # * update(tag, attrs): user requested to update a Tag (Backbone.Model) with
  #   new attributes.
  # * export: user requested to export the list of tags to CSV
  class TagList extends Backbone.View
    className: 'vertical-tag-list'

    events:
      'click a.remove': '_onClickRemove'
      'submit tfoot form': '_onSubmitNewTag'
      'submit tbody form': '_onSubmitExistingTag'
      'change tbody form': '_onChange'

    tagTemplate: _.template("""
      <tr class="existing" data-cid="<%= tag.cid %>">
        <td class="name">
          <span class="name" style="display:none;"><%- tag.get('name') %></span>
          <form method="post" action="#">
            <%= window.csrfTokenHtml %>
            <input type="hidden" name="id" value="<%- tag.id || '' %>" />
            <div class="input-group">
              <span class="input-group-addon">
                <input type="color" name="color" value="<%- tag.get('color') %>"/>
              </span>
              <input type="text" class="input-sm form-control" name="name" value="<%- tag.get('name') %>" />
            </div>
          </form>
        </td>
        <% if (hasSeparateTreeCount) { %>
          <td class="tree-count">
            <% if (_.isNumber(tag.get('sizeInTree'))) { %>
              <%- t('n_documents', tag.get('sizeInTree')) %>
            <% } else { %>
              <%- t('loading_n_documents') %>
            <% } %>
          </td>
        <% } %>
        <td class="count">
          <% if (_.isNumber(tag.get('size'))) { %>
            <%- t('n_documents', tag.get('size')) %>
          <% } else { %>
            <%- t('loading_n_documents') %>
          <% } %>
        </td>
        <td class="actions"><a class="remove" href="#"><%- t('remove') %></a></td>
      </tr>
    """)

    template: _.template("""
      <p class="preamble">
        <%- t('preamble') %>
        <% if (tags.length && exportUrl) { %>
          <a class="export" href="<%- exportUrl %>"><%- t('export') %></a>
        <% } %>
      </p>
      <table>
        <thead>
          <% if (hasSeparateTreeCount) { %>
            <tr>
              <th rowspan="2" class="name"><%- t('th.name') %></th>
              <th colspan="2" class="count-top"><%- t('th.count') %></th>
              <th rowspan="2"/>
            </tr>
            <tr>
              <th class="tree-count"><%- t('th.count_in_tree') %></th>
              <th class="count"><%- t('th.count_in_docset') %></th>
            </tr>
          <% } else { %>
            <tr>
              <th class="name"><%- t('th.name') %></th>
              <th class="count"><%- t('th.count') %></th>
              <th/>
            </tr>
          <% } %>
        </thead>
        <tbody>
          <%= tags.map(renderTag).join('') %>
        </tbody>
        <tfoot>
          <tr>
            <td colspan="<%- hasSeparateTreeCount ? 4 : 3 %>">
              <form method="post" action="#" role="form">
                <div class="input-group">
                  <input type="text" class="form-control input-sm" name="name" required="required" placeholder="<%- t('tag_name.placeholder') %>" />
                  <span class="input-group-btn">
                    <button type="submit" class="btn btn-sm"><%- t('submit') %></button>
                  </span>
                </div>
              </form>
            </td>
          </tr>
        </tfoot>
      </table>
    """)

    initialize: ->
      throw 'must set options.collection' if !@options.collection

      @listenTo(@collection, 'add', @_addTag)
      @listenTo(@collection, 'remove', @_removeTag)
      @listenTo(@collection, 'change', @_changeTag)
      @listenTo(@collection, 'reset', @render)
      @render()

    _shouldHaveSeparateTreeCount: ->
      @collection.find((t) -> t.get('sizeInTree') != t.get('size'))?

    render: ->
      removeSpectrum(@$('input[type=color]'))
      @hasSeparateTreeCount = @_shouldHaveSeparateTreeCount()
      html = @template
        t: t
        tags: @collection
        exportUrl: @options.exportUrl
        hasSeparateTreeCount: @hasSeparateTreeCount
        renderTag: (tag) => @tagTemplate
          tag: tag
          t: t
          hasSeparateTreeCount: @hasSeparateTreeCount
      @$el.html(html)
      addSpectrum(@$('input[type=color]'))
      this

    remove: ->
      removeSpectrum(@$('input[type=color]'))
      Backbone.View.prototype.remove.call(this)

    _$trForTag: (tag) ->
      @$("[data-cid=#{tag.cid}]")

    _addTag: (tag) ->
      html = @tagTemplate
        t: t
        tag: tag
        hasSeparateTreeCount: @hasSeparateTreeCount

      index = @collection.indexOf(tag)
      if index == 0
        @$('tbody').prepend(html)
      else
        @$("tbody tr:eq(#{index - 1})").after(html)
      addSpectrum(@$('input[type=color]'))

    _removeTag: (tag) ->
      $tr = @_$trForTag(tag)
      removeSpectrum($tr.find('input[type=color]'))
      $tr.remove()

    _changeTag: (tag, options) ->
      $tr = @_$trForTag(tag)
      $tr.find('input[name=id]').val(tag.id)
      $tr.find('.count').html(t('n_documents', tag.get('size') || 0))
      $tr.find('.tree-count').html(t('n_documents', tag.get('sizeInTree') || 0))

      if !options? || !options.interacting
        $tr.find('input[name=name]').val(tag.get('name'))
        $color = $tr.find('input[name=color]')
        $color.val(tag.get('color'))
        updateSpectrum($color)

    _onClickRemove: (e) ->
      e.preventDefault()
      cid = $(e.target).closest('[data-cid]').attr('data-cid')

      tag = @collection.get(cid)
      count = tag.get('size') || 0
      message = t('remove.confirm', tag.get('name'), count)

      @trigger('remove', tag) if confirm(message)

    _onSubmitNewTag: (e) ->
      e.preventDefault()

      $form = $(e.currentTarget)
      $name = $form.find('input[name=name]')
      $color = $form.find('input[name=color]')

      name = $name.val().replace(/^\s*(.*?)\s*$/, '$1')
      color = $color.val()

      if !name
        $name.focus()
      else
        tag =
          name: name
          color: color

        @trigger('add', tag)
        $form[0].reset()

    _onSubmitExistingTag: (e) ->
      e.preventDefault()
      $form = $(e.currentTarget).closest('form')

      $tr = $(e.currentTarget).closest('[data-cid]')
      cid = $tr.attr('data-cid')
      tag = @collection.get(cid)

      attrs = {
        name: $form.find('input[name=name]').val()
        color: $form.find('input[name=color]').val()
      }

      @trigger('update', tag, attrs)

    _onChange: (e) ->
      $form = $(e.currentTarget).closest('form')
      $form.submit()
