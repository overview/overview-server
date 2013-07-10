Feature: Show my document sets
  In order to visualize document sets
  As a user
  I need to see my document sets

  Scenario: Viewing the index
    Given there is a document set "my document set" owned by "user@example.org"
      And I am logged in as "user@example.org"
    When I browse to the document set index
    Then I should see a document set "my document set"
