Feature: Upload a CSV
  In order to visualize a document set
  As a user
  I need to import a document set from my computer

  Scenario: Previewing a CSV upload
    Given I am logged in as "user@example.org"
    When I browse to the document set index
     And I click the "CSV upload" link
     And I wait for all animations to complete
     And I choose the file "CsvUpload.csv"
    Then I should see a CSV preview
     And I should see an enabled "Upload" button

  Scenario: Resetting a CSV upload
    Given I am logged in as "user@example.org"
    When I browse to the document set index
     And I click the "CSV upload" link
     And I wait for all animations to complete
     And I choose the file "CsvUpload.csv"
     And I click the "Reset" button
    Then I should not see a CSV preview
     And I should not see an enabled "Upload" button

  # We can't test the AJAX-upload progress bar feature unless we create a
  # large file. Not today ... maybe after we rewrite the whole UI to use
  # Backbone?

  @worker
  Scenario: Creating a CsvImport DocumentSet
    Given I am logged in as "user@example.org"
    When I wait for a CSV upload with the file "CsvUpload.csv" to complete
    Then I should see the document set "CsvUpload.csv"

  @worker
  Scenario: Viewing a CsvImport DocumentSet
    Given I am logged in as "user@example.org"
    When I wait for a CSV upload with the file "CsvUpload.csv" to complete
     And I click the "CsvUpload.csv" link
     And I wait for all AJAX requests to complete
    Then I should see the Facebook document "facebook1"
     And I should see the Twitter document "twitter1"
     And I should see the DocumentCloud document "documentcloud1"
     And I should see the secure document "secure1"
     And I should see the insecure document "insecure1"

  @worker
  Scenario: Importing tags
    Given I am logged in as "user@example.org"
    When I wait for a CSV upload with the file "CsvUploadWithTags.csv" to complete
     And I click the "CsvUploadWithTags.csv" link
     And I wait for all AJAX requests to complete
    Then I should see the tag "tag1"
     And I should see the tag "tag2"
     And tag "tag1" should have 4 documents
     And tag "tag2" should have 3 documents


