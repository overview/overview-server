module SessionHelpers
  module WaitHelper
    def wait_for_javascript_to_return_true(script, options)
      raise ArgumentError.new("Missing options[:wait]") if !options[:wait]
      start = Time.now
      while Time.now - start < options[:wait]
        if evaluate_script(script)
          return true
        else
          sleep 0.05
        end
      end
      raise Exception.new("JavaScript `#{script}` did not return true, even after #{options[:wait]}s")
    end

    # Runs the given block and stalls until animations on selector stop.
    #
    # Assumes animations will *start* on the selected elements, and that the
    # elements will remain in the DOM until the animations stop.
    #
    # Also assumes selector doesn't include apostrophes.
    #
    # Also assumes no elements have two transition speeds/delays. In other
    # words: if the border and width transitions on an element end at
    # different times, you'll get undefined behavior.
    #
    # Why add all these assumptions, instead of simply waiting for all CSS
    # transitions to end? Because Chromium 63 doesn't support transitionstart or
    # transitionrun. We implement the workaround similar to
    # https://bugs.chromium.org/p/chromium/issues/detail?id=439056
    # which means we:
    #
    # 1. Store the matching HTML elements in a global variable
    # 2. Add a transitionend listener to each matching HTML element to remove
    #    it from the global (and nix the listener)
    # 3. Wait until the global is empty
    def waiting_for_css_transitions_on_selector(selector, &block)
      varname = "waiting_for_css_transitions_#{rand(9999999)}"

      evaluate_script(<<-EOT
        (function() {
          const els = window.#{varname} = Array.from(document.querySelectorAll('#{selector}'))
          function listener(ev) {
            const el = ev.target
            const index = els.indexOf(el)
            if (index === -1) return
            els.splice(index, 1)
            el.removeEventListener('transitionend', listener)
          }
          els.forEach(el => el.addEventListener('transitionend', listener))
        })()
      EOT
      )

      yield

      js = <<-EOT
        (function() {
          if (!window.#{varname}) true
          if (window.#{varname}.length === 0) {
            delete window.#{varname}
            return true
          } else {
            return false
          }
        })()
      EOT
      wait_for_javascript_to_return_true(js, wait: WAIT_TRANSITION)
    end
  end
end
