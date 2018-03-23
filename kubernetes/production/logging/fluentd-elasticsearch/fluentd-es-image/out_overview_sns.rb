require 'fluent/plugin/output'
require 'aws-sdk-sns'

module Fluent
  module Plugin
    class OverviewSns < Output
      Fluent::Plugin.register_output('overview_sns', self)

      def start
        super

        sns = Aws::SNS::Resource.new
        topic_name = 'Overview'
        @topic = sns.topics.find { |t| t.arn =~ /:Overview\Z/ }
        raise ConfigError, "Could not find SNS topic '#{topic_name}'" if @topic.nil?
      end

      def write(chunk)
        chunk.each { |time, record| write_record(time, record) }
      end

      private

      def write_record(time, record)
        message = "#{record['log']}\n\nFor context:\n\n1. `kubectl proxy`\n2. Browse to http://localhost:8001/api/v1/namespaces/kube-system/services/kibana-logging/proxy/app/kibana"
        @topic.publish(message: message)
      end
    end
  end
end
