Feature: rendering executable specifications
  Example: simple Gherkin example
    Given a project with a feature file
      """feature
      Feature: smurfing
        Example: happy flow
          Given a smurf
          When I smurf the smurf
          Then the smurf is smurfed
      """
    When I run the test suite
    And I package the documentation
    Then the package contains the lines from this feature file
