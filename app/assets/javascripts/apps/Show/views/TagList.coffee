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
        <td class="tree-count">⋯</td>
        <td class="count">⋯</td>
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
        <thead class="tree-count">
          <tr>
            <th rowspan="2" class="name"><%- t('th.name') %></th>
            <th colspan="2" class="count-top"><%- t('th.count') %></th>
            <th rowspan="2"/>
          </tr>
          <tr>
            <th class="tree-count"><%- t('th.count_in_tree') %></th>
            <th class="count"><%- t('th.count_in_docset') %></th>
          </tr>
        </thead>
        <thead class="no-tree-count">
          <tr>
            <th class="name"><%- t('th.name') %></th>
            <th class="count-top"><%- t('th.count') %></th>
            <th/>
          </tr>
        </thead>
        <tbody>
          <%= tags.map(renderTag).join('') %>
        </tbody>
        <tfoot>
          <tr>
            <td colspan="4">
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
      @listenTo(@collection, 'reset', @_resetTags)

      # Hash of tag CID => { $tr, $id, $color, $name, $count, $treeCount, count, treeCount }
      #
      # Invariant: after any method, @tags reflects what's in the DOM.
      @tags = {}

      @render()

    render: ->
      removeSpectrum(@$('input[type=color]'))
      html = @template
        t: t
        tags: @collection
        exportUrl: @options.exportUrl
        renderTag: (tag) => @tagTemplate
          tag: tag
          t: t
      @$el.html(html)

      @_addTrToTags(tr) for tr in @$('tr[data-cid]')

      addSpectrum(tag.$color) for _, tag of @tags

      this

    _addTrToTags: (tr) ->
      $tr = $(tr)
      @tags[$tr.attr('data-cid')] =
        $tr: $tr
        $id: $tr.find('input[name=id]')
        $name: $tr.find('input[name=name]')
        $color: $tr.find('input[type=color]')
        $count: $tr.find('.count')
        $treeCount: $tr.find('.tree-count')
        count: null
        treeCount: null

    # Fills in tree counts for each tag.
    renderCounts: (counts) ->
      for tagId, values of counts
        if (cid = @collection.get(tagId)?.cid)?
          data = @tags[cid]
          data.count = values.size           # null means unknown; 0 means 0
          data.treeCount = values.sizeInTree # null means unknown; 0 means 0
          data.$count.text(t('n_documents', data.count ? 0))
          data.$treeCount.text(t('n_documents', data.treeCount ? data.count ? 0))
      undefined

      @_refreshHasTreeCounts()

    # Toggles the 'has-tree-counts' class on the table.
    #
    # After calling this method, the class will be set iff there is a tag that
    # has a different count in the tree than in the document set.
    _refreshHasTreeCounts: ->
      needed = false
      for _, data of @tags
        if data.treeCount? && data.count != data.treeCount
          needed = true
          break

      @$('table').toggleClass('has-tree-counts', needed)

    remove: ->
      removeSpectrum(@$('input[type=color]'))
      Backbone.View.prototype.remove.call(this)

    _addTag: (tag) ->
      html = @tagTemplate
        t: t
        tag: tag
      $tr = $(html)

      index = @collection.indexOf(tag)
      if index == 0
        @$('tbody').prepend($tr)
      else
        @$("tbody tr:eq(#{index - 1})").after($tr)

      tagData = @_addTrToTags($tr)
      addSpectrum(tagData.$color)

      # Show counts as 0 by default.
      #
      # This is a workaround for a race:
      #
      # 1. Open new-tag dialog, spinning off "count" HTTP request
      # 2. Create a tag, spinning off a "create" HTTP request, which waits
      # 3. Receive HTTP response for "count"
      # ...
      #
      # The "count" response won't include the new tag. We used to actually
      # fetch the collection and then re-render, which hid the new tag (that's
      # https://www.pivotaltracker.com/story/show/95450308). The workaround:
      # just assume the counts are 0.
      #
      # It's not like our users expect the view to update as HTTP requests
      # complete, right?
      tagData.$count.text(t('n_documents', 0))
      tagData.$treeCount.text(t('n_documents', 0))

      @_refreshHasTreeCounts()

    _removeTag: (tag) ->
      tagData = @tags[tag.cid]
      removeSpectrum(tagData.$color)
      tagData.$tr.remove()
      delete @tags[tag.cid]

    _changeTag: (tag, options) ->
      tagData = @tags[tag.cid]
      return if !tagData

      tagData.$id.val(tag.id)

      if !options?.interacting
        tagData.$name.val(tag.get('name'))
        tagData.$color.val(tag.get('color'))
        updateSpectrum(tagData.$color)

    _resetTags: ->
      @tags = {}
      @render()

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
