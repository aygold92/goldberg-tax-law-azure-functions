package com.goldberg.law.managedagents.deployment

import com.anthropic.core.jsonMapper
import com.anthropic.models.beta.deployments.DeploymentCreateParams
import com.anthropic.models.beta.deployments.DeploymentUpdateParams
import com.goldberg.law.managedagents.Yaml
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * The deployment configs cap each run's session with a `budget`, which the SDK has no typed field for.
 * It therefore rides along as an unknown property, and this pins that pass-through: a budget silently
 * dropped on the way to the API would leave consolidation runs uncapped with nothing to show for it.
 */
class DeploymentBudgetTest {

    private val deployments = Path.of("managed-agents/deployments")

    private val expectedBudget = mapOf(
        "type" to "limit",
        // Whole US cents as a string — a number here would be rejected by the API
        "max_list_cost" to mapOf("amount" to "200", "currency" to "USD"),
    )

    private fun configs() = deployments.toFile().listFiles { f -> f.extension == "yaml" }!!.map { it.toPath() }

    @Test
    fun `every memory-consolidation deployment caps its runs at 2 dollars`() {
        val configs = configs()
        assertThat(configs).isNotEmpty()

        configs.forEach { path ->
            val budget = Yaml.read(path).get("budget")
            assertThat(budget).describedAs("budget in ${path.fileName}").isNotNull()
            assertThat(jsonMapper().convertValue(budget, Map::class.java))
                .describedAs("budget in ${path.fileName}")
                .isEqualTo(expectedBudget)
        }
    }

    @Test
    fun `the budget survives conversion into the SDK's create and update bodies`() {
        val node = Yaml.read(configs().first())

        val created = jsonMapper().convertValue(node, DeploymentCreateParams.Body::class.java)
        val updated = jsonMapper().convertValue(node, DeploymentUpdateParams.Body::class.java)

        listOf(created._additionalProperties(), updated._additionalProperties()).forEach { properties ->
            val budget = properties["budget"]
            assertThat(budget).isNotNull()
            assertThat(budget!!.convert(Map::class.java)).isEqualTo(expectedBudget)
        }
    }
}
