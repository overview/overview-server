# Adds universal methods: available in every "describe" block
module FeatureHelper
  def self.included(base)
    base.class_eval do
      include FeatureHelpers::AdminSessionHelper
      include FeatureHelpers::SessionHelper # adds "before" and "after" hooks
    end
  end
end
