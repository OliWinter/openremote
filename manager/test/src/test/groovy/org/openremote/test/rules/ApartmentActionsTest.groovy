package org.openremote.test.rules

import org.openremote.manager.server.asset.AssetProcessingService
import org.openremote.manager.server.asset.AssetStorageService
import org.openremote.manager.server.rules.RulesService
import org.openremote.manager.server.rules.RulesetStorageService
import org.openremote.manager.server.setup.SetupService
import org.openremote.manager.server.setup.builtin.KeycloakDemoSetup
import org.openremote.manager.server.setup.builtin.ManagerDemoSetup
import org.openremote.manager.server.setup.builtin.RulesDemoSetup
import org.openremote.test.ManagerContainerTrait
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class ApartmentActionsTest extends Specification implements ManagerContainerTrait {

    def "Check rules RHS action execution"() {
        given: "expected conditions"
        def conditions = new PollingConditions(timeout: 5)

        and: "the container is started"
        def serverPort = findEphemeralPort()
        def container = startContainer(defaultConfig(serverPort), defaultServices())
        def managerDemoSetup = container.getService(SetupService.class).getTaskOfType(ManagerDemoSetup.class)
        def keycloakDemoSetup = container.getService(SetupService.class).getTaskOfType(KeycloakDemoSetup.class)
        def rulesDemoSetup = container.getService(SetupService.class).getTaskOfType(RulesDemoSetup.class)
        def rulesService = container.getService(RulesService.class)
        def rulesetStorageService = container.getService(RulesetStorageService.class)
        def assetProcessingService = container.getService(AssetProcessingService.class)
        def assetStorageService = container.getService(AssetStorageService.class)

        expect: "the rule engines to become available and be running"
        conditions.eventually {
            def customerAEngine = rulesService.tenantDeployments.get(keycloakDemoSetup.customerATenant.id)
            assert customerAEngine != null
            assert customerAEngine.isRunning()
            assert customerAEngine.allRulesets.find { it.id == rulesDemoSetup.apartmentActionsRulesetId } != null
        }

        /* TODO This is broken, loading facts on startup is not done
        and: "the demo attributes marked with RULES_FACT = true meta should be inserted into the engines"
        conditions.eventually {
            assert globalEngine.facts.size() == 11
        }
        */

        // TODO Write more tests and implement RHS Assets facade

        cleanup: "the server should be stopped"
        stopContainer(container)
    }
}