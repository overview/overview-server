module FeatureHelpers
  module SessionHelper
    # Launches a new web browser. Called by `page`.
    #
    # You can optionally define an `extra_session_helpers` method (returning an
    # Array of Modules) to add methods to every returned Capybara session.
    def new_session
      ret = Capybara::Session.new(Capybara.default_driver)
      for mod in session_helpers
        ret.extend(mod)
      end
      ret
    end

    # session helpers that are included by the framework
    def default_session_helpers
      [
        SessionHelpers::LoginHelper,
        SessionHelpers::WaitHelper,
        SessionHelpers::DocumentSetHelper,
      ]
    end

    # session helpers specific to one *_spec.rb file.
    #
    # The _spec.rb file should `def extra_session_helpers; [ ... ]; end` to
    # choose which helpers to use. The methods will exist on the default `page`
    # and on any other Capybara::Session returned by `new_session`.
    def extra_session_helpers
      [
      ]
    end

    def session_helpers
      default_session_helpers + extra_session_helpers
    end

    # Launches a memoized web browser
    def page
      @page ||= new_session
    end
  end
end
