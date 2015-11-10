define [ 'i18n' ], (i18n) ->
  t = i18n.namespaced('time_display.shouldFinishIn')

  (ms) ->
    if ms < 1000
      t('zero')
    else if ms < 1000 * 60
      t('seconds', ms / 1000)
    else if ms < 1000 * 60 * 60
      t('minutes', ms / 1000 / 60)
    else
      t('hours', ms / 1000 / 60 / 60)
