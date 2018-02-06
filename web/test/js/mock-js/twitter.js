window.twttr._e.forEach(function(f) {
  window.setTimeout(f, 0)
})
window.twttr._e.splice(0, window.twttr._e.length)

window.twttr.ready = function(f) {
  window.setTimeout(f, 0)
}

window.twttr.widgets = {
  createTweet(tweetId, target, options) {
    const div = document.createElement('div')
    div.textContent = 'Rendered tweet ' + tweetId

    const deleted = /000000/.test(tweetId)

    if (deleted) div.style.visibility = 'hidden'

    target.appendChild(div)

    // [2018-02-06, adamhooper] Twitter returns undefined for a deleted tweet,
    // and it also inserts an element into the DOM.
    return Promise.resolve(deleted ? undefined : div)
  }
}
