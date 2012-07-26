Feature: Show a document set
  In order to visualize a document set and provide value to customers
  As a user
  I should be able to navigate a document set

  Scenario: Viewing the document set page
    Given there is a basic document set
    And I am logged in
    When I browse to the document set
    Then I should see the tree
    And I should see the focus slider
    And I should see the document list
