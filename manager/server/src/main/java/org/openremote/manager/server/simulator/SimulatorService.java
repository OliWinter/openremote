/*
 * Copyright 2016, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.manager.server.simulator;

import org.apache.camel.builder.RouteBuilder;
import org.openremote.agent.protocol.simulator.SimulatorProtocol;
import org.openremote.container.Container;
import org.openremote.container.ContainerService;
import org.openremote.container.message.MessageBrokerSetupService;
import org.openremote.container.security.AuthContext;
import org.openremote.manager.server.asset.AssetStorageService;
import org.openremote.manager.server.event.ClientEventService;
import org.openremote.manager.server.security.ManagerIdentityService;
import org.openremote.model.Constants;
import org.openremote.model.attribute.AttributeRef;
import org.openremote.model.simulator.RequestSimulatorState;
import org.openremote.model.simulator.SimulatorState;

import java.util.List;
import java.util.logging.Logger;

import static org.openremote.manager.server.event.ClientEventService.CLIENT_EVENT_TOPIC;
import static org.openremote.manager.server.event.ClientEventService.getSessionKey;

/**
 * Connects the client/UI to the {@link SimulatorProtocol}.
 */
public class SimulatorService extends RouteBuilder implements ContainerService {

    private static final Logger LOG = Logger.getLogger(SimulatorService.class.getName());

    protected ManagerIdentityService managerIdentityService;
    protected AssetStorageService assetStorageService;
    protected ClientEventService clientEventService;
    protected SimulatorProtocol simulatorProtocol;

    @Override
    public void init(Container container) throws Exception {
        managerIdentityService = container.getService(ManagerIdentityService.class);
        assetStorageService = container.getService(AssetStorageService.class);
        clientEventService = container.getService(ClientEventService.class);
        simulatorProtocol = container.getService(SimulatorProtocol.class);

        clientEventService.addSubscriptionAuthorizer((auth, subscription) -> {
            if (!subscription.isEventType(SimulatorState.class))
                return false;

            // Superuser can get all
            if (auth.isSuperUser())
                return true;

            // TODO Should realm admins be able to work with simulators in their tenant?

            return false;
        });

        container.getService(MessageBrokerSetupService.class).getContext().addRoutes(this);

        // When a protocol instance has its values updated through linked attribute writes, publish a snapshot to all sessions
        simulatorProtocol.setValuesChangedHandler(
            protocolConfiguration -> publishSimulatorState(null, protocolConfiguration)
        );
    }

    @Override
    public void start(Container container) throws Exception {

    }

    @Override
    public void stop(Container container) throws Exception {

    }

    @Override
    public void configure() throws Exception {
        from(CLIENT_EVENT_TOPIC)
            .routeId("FromClientSimulatorRequests")
            .filter(body().isInstanceOf(RequestSimulatorState.class))
            .process(exchange -> {
                RequestSimulatorState event = exchange.getIn().getBody(RequestSimulatorState.class);
                LOG.fine("Handling from client: " + event);

                String sessionKey = getSessionKey(exchange);
                AuthContext authContext = exchange.getIn().getHeader(Constants.AUTH_CONTEXT, AuthContext.class);

                // Superuser can get all
                if (!authContext.isSuperUser())
                    return;

                // TODO Should realm admins be able to work with simulators in their tenant?

                for (AttributeRef protocolConfiguration : event.getConfigurations()) {
                    publishSimulatorState(sessionKey, protocolConfiguration);
                }
            });

        from(CLIENT_EVENT_TOPIC)
            .routeId("FromClientSimulatorState")
            .filter(body().isInstanceOf(SimulatorState.class))
            .process(exchange -> {
                SimulatorState event = exchange.getIn().getBody(SimulatorState.class);
                LOG.fine("Handling from client: " + event);

                AuthContext authContext = exchange.getIn().getHeader(Constants.AUTH_CONTEXT, AuthContext.class);

                // Superuser can get all
                if (!authContext.isSuperUser())
                    return;

                // TODO Should realm admins be able to work with simulators in their tenant?

                simulatorProtocol.updateSimulatorState(event);
            });
    }

    protected void publishSimulatorState(String sessionKey, AttributeRef protocolConfiguration) {
        LOG.fine("Attempting to publish simulator state: " + protocolConfiguration);
        simulatorProtocol.getSimulatorState(protocolConfiguration).ifPresent(simulatorState -> {
            // We need asset names instead of identifiers for user-friendly display
            simulatorState.updateAssetNames(assetIdAndNames -> {
                String[] assetIds = assetIdAndNames.keySet().toArray(new String[0]);
                List<String> assetNames = assetStorageService.findNames(assetIds);
                for (int i = 0; i < assetIds.length; i++) {
                    String assetId = assetIds[i];
                    assetIdAndNames.put(assetId, assetNames.get(i));
                }
            });
            if (sessionKey != null) {
                clientEventService.sendToSession(sessionKey, simulatorState);
            } else {
                clientEventService.publishEvent(simulatorState);
            }
        });
    }
}
