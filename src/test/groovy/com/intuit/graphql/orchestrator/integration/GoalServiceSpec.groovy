package com.intuit.graphql.orchestrator.integration

import com.google.common.collect.ImmutableMap
import com.intuit.graphql.orchestrator.GoalsService
import com.intuit.graphql.orchestrator.GraphQLOrchestrator
import com.intuit.graphql.orchestrator.ServiceProvider
import graphql.ExecutionInput
import helpers.BaseIntegrationTestSpecification

class GoalServiceSpec extends BaseIntegrationTestSpecification {

    def "can Query Goal Service"() {
        given:
        ServiceProvider[] services = [ new GoalsService() ]
        final GraphQLOrchestrator orchestrator = createGraphQLOrchestrator(services)

        // Test query using ExecutionInput
        ExecutionInput booksAndPetsEI = ExecutionInput.newExecutionInput().query('''
            query goalsQuery($goalId: Long) {
                userGoals {
                    id creationTime linkedProviders {
                        id
                        name
                        ... on DebtProvider {
                            currentValue
                        }
                    }
                }
                userGoalImages(userGoalId: $goalId) {
                    imageUrl
                    imageBlob
                }
            }
        ''')
        .variables(ImmutableMap.of("goalId", Long.valueOf(1)))
        .build()

        when:
        Map<String, Object> executionResult = orchestrator.execute(booksAndPetsEI).get()
        .toSpecification()

        then:
        executionResult.get("errors") == null
        executionResult.get("data") != null

        Map<String, Object> data = (Map<String, Object>) executionResult.get("data")

        List<Map<String, Object>> userGoals = (List<Map<String, Object>>) data.get("userGoals")
        userGoals.size() == 2
        userGoals.get(0).get("__typename") == null
        userGoals.get(0).get("id") != null
        userGoals.get(0).get("creationTime") != null
        ((List)userGoals.get(0).get("linkedProviders")).size() == 1

        List<Map<String, Object>> linkedProviders0 = (List) userGoals.get(0).get("linkedProviders")
        linkedProviders0.size() == 1
        ((Map)linkedProviders0.get(0)).get("id") == "dp-1"
        ((Map)linkedProviders0.get(0)).get("name") == "some debt provider"
        ((Map)linkedProviders0.get(0)).get("currentValue") == new BigDecimal(1.5)

        userGoals.get(1).get("__typename") == null
        userGoals.get(1).get("id") != null
        userGoals.get(1).get("creationTime") != null
        ((List) userGoals.get(1).get("linkedProviders")).size() == 1

        List<Map<String, Object>> linkedProviders1 = (List) userGoals.get(1).get("linkedProviders")
        linkedProviders1.size() == 1
        ((Map)linkedProviders1.get(0)).get("id") == "dp-2"
        ((Map)linkedProviders1.get(0)).get("name") == "some debt provider 2"
        ((Map)linkedProviders1.get(0)).get("currentValue") == new BigDecimal(5.0)

        List<Map<String, Object>> userGoalImages = (List<Map<String, Object>>) data.get("userGoalImages")
        userGoalImages.size() == 1
        userGoalImages.get(0).get("imageUrl") == "SomeImageUrl"
        userGoalImages.get(0).get("imageBlob") == "SomeImageBlob"
    }

}
