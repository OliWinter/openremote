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
package org.openremote.manager.server.setup.builtin;

import com.vividsolutions.jts.geom.Coordinate;
import org.openremote.agent.protocol.simulator.SimulatorProtocol;
import org.openremote.container.Container;
import org.openremote.manager.server.asset.ServerAsset;
import org.openremote.manager.server.setup.AbstractManagerSetup;
import org.openremote.manager.shared.security.Tenant;
import org.openremote.model.asset.AssetAttribute;
import org.openremote.model.asset.AssetState;
import org.openremote.model.attribute.*;
import org.openremote.model.simulator.element.ColorSimulatorElement;
import org.openremote.model.simulator.element.NumberSimulatorElement;
import org.openremote.model.simulator.element.SwitchSimulatorElement;
import org.openremote.model.value.Values;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import static org.openremote.model.asset.AssetMeta.*;
import static org.openremote.model.asset.AssetType.*;
import static org.openremote.model.asset.agent.ProtocolConfiguration.initProtocolConfiguration;
import static org.openremote.model.attribute.AttributeType.*;

public class ManagerDemoSetup extends AbstractManagerSetup {

    final protected boolean importDemoScenes;

    // Update these numbers whenever you change a RULE_STATE flag in test data
    public static final int DEMO_RULE_STATES_APARTMENT_1 = 15;
    public static final int DEMO_RULE_STATES_APARTMENT_2 = 9;
    public static final int DEMO_RULE_STATES_APARTMENT_3 = 0;
    public static final int DEMO_RULE_STATES_SMART_HOME = DEMO_RULE_STATES_APARTMENT_1 + DEMO_RULE_STATES_APARTMENT_2 + DEMO_RULE_STATES_APARTMENT_3;
    public static final int DEMO_RULE_STATES_CUSTOMER_A = DEMO_RULE_STATES_SMART_HOME;

    public static final int DEMO_RULE_STATES_GLOBAL = DEMO_RULE_STATES_CUSTOMER_A;

    public static final int DEMO_RULE_STATES_APARTMENT_1_WITH_SCENES = DEMO_RULE_STATES_APARTMENT_1 + 28;
    public static final int DEMO_RULE_STATES_SMART_HOME_WITH_SCENES = DEMO_RULE_STATES_APARTMENT_1_WITH_SCENES + DEMO_RULE_STATES_APARTMENT_2 + DEMO_RULE_STATES_APARTMENT_3;
    public static final int DEMO_RULE_STATES_CUSTOMER_A_WITH_SCENES = DEMO_RULE_STATES_SMART_HOME_WITH_SCENES;
    public static final int DEMO_RULE_STATES_GLOBAL_WITH_SCENES = DEMO_RULE_STATES_CUSTOMER_A_WITH_SCENES;

    public String smartOfficeId;
    public String groundFloorId;
    public String lobbyId;
    public String agentId;
    public final String agentProtocolConfigName = "simulator123";
    public String thingId;
    public String smartHomeId;
    public String apartment1Id;
    public String apartment1ServiceAgentId;
    public String apartment1LivingroomId;
    public String apartment1KitchenId;
    public String apartment2Id;
    public String apartment3Id;
    public String apartment2LivingroomId;
    public String apartment2BathroomId;
    public String apartment3LivingroomId;
    public String masterRealmId;
    public String customerARealmId;

    public ManagerDemoSetup(Container container, boolean importDemoScenes) {
        super(container);
        this.importDemoScenes = importDemoScenes;
    }

    @Override
    public void execute() throws Exception {

        KeycloakDemoSetup keycloakDemoSetup = setupService.getTaskOfType(KeycloakDemoSetup.class);
        Tenant masterTenant = keycloakDemoSetup.masterTenant;
        Tenant customerATenant = keycloakDemoSetup.customerATenant;
        masterRealmId = masterTenant.getId();
        customerARealmId = customerATenant.getId();

        // ################################ Demo assets for 'master' realm ###################################


        ServerAsset smartOffice = new ServerAsset();
        smartOffice.setRealmId(masterTenant.getId());
        smartOffice.setName("Smart Office");
        smartOffice.setLocation(geometryFactory.createPoint(new Coordinate(5.460315214821094, 51.44541688237109)));
        smartOffice.setType(BUILDING);
        List<AssetAttribute> smartOfficeAttributes = Arrays.asList(
            new AssetAttribute("geoStreet", STRING, Values.create("Torenallee 20"))
                .setMeta(
                    new MetaItem(LABEL, Values.create("Street")),
                    new MetaItem(ABOUT, Values.create("http://project-haystack.org/tag/geoStreet"))
                ),
            new AssetAttribute("geoPostalCode", AttributeType.NUMBER, Values.create(5617))
                .setMeta(
                    new MetaItem(LABEL, Values.create("Postal Code")),
                    new MetaItem(ABOUT, Values.create("http://project-haystack.org/tag/geoPostalCode"))
                ),
            new AssetAttribute("geoCity", STRING, Values.create("Eindhoven"))
                .setMeta(
                    new MetaItem(LABEL, Values.create("City")),
                    new MetaItem(ABOUT, Values.create("http://project-haystack.org/tag/geoCity"))
                ),
            new AssetAttribute("geoCountry", STRING, Values.create("Netherlands"))
                .setMeta(
                    new MetaItem(LABEL, Values.create("Country")),
                    new MetaItem(ABOUT, Values.create("http://project-haystack.org/tag/geoCountry"))
                )
        );

        smartOffice.setAttributes(smartOfficeAttributes);
        smartOffice = assetStorageService.merge(smartOffice);
        smartOfficeId = smartOffice.getId();

        ServerAsset groundFloor = new ServerAsset("Ground Floor", FLOOR, smartOffice);
        groundFloor.setLocation(smartOffice.getLocation());
        groundFloor = assetStorageService.merge(groundFloor);
        groundFloorId = groundFloor.getId();

        ServerAsset lobby = new ServerAsset("Lobby", ROOM, groundFloor);
        lobby.setLocation(groundFloor.getLocation());
        lobby = assetStorageService.merge(lobby);
        lobbyId = lobby.getId();

        ServerAsset agent = new ServerAsset("Demo Agent", AGENT, lobby);
        agent.setLocation(lobby.getLocation());
        agent.setAttributes(
            initProtocolConfiguration(new AssetAttribute(agentProtocolConfigName), SimulatorProtocol.PROTOCOL_NAME)
                .addMeta(
                    new MetaItem(
                        SimulatorProtocol.CONFIG_MODE,
                        Values.create(SimulatorProtocol.Mode.WRITE_THROUGH_DELAYED.toString())
                    ),
                    new MetaItem(
                        SimulatorProtocol.CONFIG_WRITE_DELAY_MILLISECONDS,
                        Values.create(500)
                    ))
        );

        agent = assetStorageService.merge(agent);
        agentId = agent.getId();

        ServerAsset thing = new ServerAsset("Demo Thing", THING, agent);
        thing.setLocation(agent.getLocation());
        thing.setAttributes(
            new AssetAttribute("light1Toggle", BOOLEAN, Values.create(true))
                .setMeta(new Meta(
                    new MetaItem(
                        LABEL,
                        Values.create("Light 1 Toggle")),
                    new MetaItem(
                        DESCRIPTION,
                        Values.create("Switch for living room light")),
                    new MetaItem(
                        STORE_DATA_POINTS,
                        Values.create(true)),
                    new MetaItem(
                        AGENT_LINK,
                        new AttributeRef(agent.getId(), agentProtocolConfigName).toArrayValue()),
                    new MetaItem(
                        SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(SwitchSimulatorElement.ELEMENT_NAME)
                    ))
                ),
            new AssetAttribute("light1Dimmer", PERCENTAGE) // No initial value!
                .setMeta(new Meta(
                        new MetaItem(
                            LABEL,
                            Values.create("Light 1 Dimmer")),
                        new MetaItem(
                            DESCRIPTION,
                            Values.create("Dimmer for living room light")),
                        new MetaItem(
                            RANGE_MIN,
                            Values.create(0)),
                        new MetaItem(
                            RANGE_MAX,
                            Values.create(100)),
                        new MetaItem(
                            AGENT_LINK,
                            new AttributeRef(agent.getId(), agentProtocolConfigName).toArrayValue()),
                        new MetaItem(
                            SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(NumberSimulatorElement.ELEMENT_NAME_RANGE)),
                        new MetaItem(
                            SimulatorProtocol.CONFIG_MODE, Values.create(SimulatorProtocol.Mode.WRITE_THROUGH_DELAYED.toString()))
                    )
                ),
            new AssetAttribute("light1Color", COLOR_RGB, new ColorRGB(88, 123, 88).asArrayValue())
                .setMeta(new Meta(
                        new MetaItem(
                            LABEL,
                            Values.create("Light 1 Color")),
                        new MetaItem(
                            DESCRIPTION,
                            Values.create("Color of living room light")),
                        new MetaItem(
                            AGENT_LINK,
                            new AttributeRef(agent.getId(), agentProtocolConfigName).toArrayValue()),
                        new MetaItem(
                            SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(ColorSimulatorElement.ELEMENT_NAME))
                    )
                ),
            new AssetAttribute("light1PowerConsumption", ENERGY_KWH, Values.create(12.345))
                .setMeta(new Meta(
                        new MetaItem(
                            LABEL,
                            Values.create("Light 1 Usage")),
                        new MetaItem(
                            DESCRIPTION,
                            Values.create("Total energy consumption of living room light")),
                        new MetaItem(
                            READ_ONLY,
                            Values.create(true)),
                        new MetaItem(
                            FORMAT,
                            Values.create("%3d kWh")),
                        new MetaItem(
                            AGENT_LINK,
                            new AttributeRef(agent.getId(), agentProtocolConfigName).toArrayValue()),
                        new MetaItem(
                            SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(NumberSimulatorElement.ELEMENT_NAME)),
                        new MetaItem(
                            STORE_DATA_POINTS, Values.create(true))
                    )
                )
        );
        thing = assetStorageService.merge(thing);
        thingId = thing.getId();

        // Some sample datapoints
        thing = assetStorageService.find(thingId, true);
        ZonedDateTime now = LocalDateTime.now().atZone(ZoneId.systemDefault());

        AssetAttribute light1PowerConsumptionAttribute = thing.getAttribute("light1PowerConsumption")
            .orElseThrow(() -> new RuntimeException("Invalid test data"));

        assetDatapointService.accept(new AssetState(thing, light1PowerConsumptionAttribute, AttributeEvent.Source.SENSOR));

        light1PowerConsumptionAttribute.setValue(Values.create(0.11), now.minusDays(80).toEpochSecond() * 1000);
        assetDatapointService.accept(new AssetState(thing, light1PowerConsumptionAttribute, AttributeEvent.Source.SENSOR));

        light1PowerConsumptionAttribute.setValue(Values.create(1.22), now.minusDays(40).toEpochSecond() * 1000);
        assetDatapointService.accept(new AssetState(thing, light1PowerConsumptionAttribute, AttributeEvent.Source.SENSOR));

        light1PowerConsumptionAttribute.setValue(Values.create(2.33), now.minusDays(20).toEpochSecond() * 1000);
        assetDatapointService.accept(new AssetState(thing, light1PowerConsumptionAttribute, AttributeEvent.Source.SENSOR));

        light1PowerConsumptionAttribute.setValue(Values.create(3.44), now.minusDays(10).toEpochSecond() * 1000);
        assetDatapointService.accept(new AssetState(thing, light1PowerConsumptionAttribute, AttributeEvent.Source.SENSOR));

        light1PowerConsumptionAttribute.setValue(Values.create(4.55), now.minusDays(8).toEpochSecond() * 1000);

        light1PowerConsumptionAttribute.setValue(Values.create(5.66), now.minusDays(6).toEpochSecond() * 1000);
        assetDatapointService.accept(new AssetState(thing, light1PowerConsumptionAttribute, AttributeEvent.Source.SENSOR));

        light1PowerConsumptionAttribute.setValue(Values.create(6.77), now.minusDays(3).toEpochSecond() * 1000);
        assetDatapointService.accept(new AssetState(thing, light1PowerConsumptionAttribute, AttributeEvent.Source.SENSOR));

        light1PowerConsumptionAttribute.setValue(Values.create(7.88), now.minusDays(1).toEpochSecond() * 1000);
        assetDatapointService.accept(new AssetState(thing, light1PowerConsumptionAttribute, AttributeEvent.Source.SENSOR));

        light1PowerConsumptionAttribute.setValue(Values.create(8.99), now.minusHours(10).toEpochSecond() * 1000);
        assetDatapointService.accept(new AssetState(thing, light1PowerConsumptionAttribute, AttributeEvent.Source.SENSOR));

        light1PowerConsumptionAttribute.setValue(Values.create(9.11), now.minusHours(5).toEpochSecond() * 1000);
        assetDatapointService.accept(new AssetState(thing, light1PowerConsumptionAttribute, AttributeEvent.Source.SENSOR));

        light1PowerConsumptionAttribute.setValue(Values.create(10.22), now.minusHours(2).toEpochSecond() * 1000);
        assetDatapointService.accept(new AssetState(thing, light1PowerConsumptionAttribute, AttributeEvent.Source.SENSOR));

        light1PowerConsumptionAttribute.setValue(Values.create(11.33), now.minusHours(1).toEpochSecond() * 1000);
        assetDatapointService.accept(new AssetState(thing, light1PowerConsumptionAttribute, AttributeEvent.Source.SENSOR));

        light1PowerConsumptionAttribute.setValue(Values.create(11.44), now.minusMinutes(30).toEpochSecond() * 1000);
        assetDatapointService.accept(new AssetState(thing, light1PowerConsumptionAttribute, AttributeEvent.Source.SENSOR));

        light1PowerConsumptionAttribute.setValue(Values.create(12.00), now.minusMinutes(5).toEpochSecond() * 1000);
        assetDatapointService.accept(new AssetState(thing, light1PowerConsumptionAttribute, AttributeEvent.Source.SENSOR));

        light1PowerConsumptionAttribute.setValue(Values.create(12.11), now.minusSeconds(5).toEpochSecond() * 1000);
        assetDatapointService.accept(new AssetState(thing, light1PowerConsumptionAttribute, AttributeEvent.Source.SENSOR));

        light1PowerConsumptionAttribute.setValue(Values.create(12.22), now.minusSeconds(1).toEpochSecond() * 1000);
        assetDatapointService.accept(new AssetState(thing, light1PowerConsumptionAttribute, AttributeEvent.Source.SENSOR));

        // ################################ Demo assets for 'customerA' realm ###################################

        ServerAsset smartHome = new ServerAsset();
        smartHome.setRealmId(customerATenant.getId());
        smartHome.setName("Smart Home");
        smartHome.setLocation(geometryFactory.createPoint(new Coordinate(5.470945, 51.438000)));
        smartHome.setType(BUILDING);
        smartHome.setAttributes(
            new AssetAttribute("geoStreet", STRING, Values.create("Wilhelminaplein 21C"))
                .setMeta(
                    new MetaItem(LABEL, Values.create("Street")),
                    new MetaItem(ABOUT, Values.create("http://project-haystack.org/tag/geoStreet"))
                ),
            new AssetAttribute("geoPostalCode", AttributeType.NUMBER, Values.create(5611))
                .setMeta(
                    new MetaItem(LABEL, Values.create("Postal Code")),
                    new MetaItem(ABOUT, Values.create("http://project-haystack.org/tag/geoPostalCode"))
                ),
            new AssetAttribute("geoCity", STRING, Values.create("Eindhoven"))
                .setMeta(
                    new MetaItem(LABEL, Values.create("City")),
                    new MetaItem(ABOUT, Values.create("http://project-haystack.org/tag/geoCity"))
                ),
            new AssetAttribute("geoCountry", STRING, Values.create("Netherlands"))
                .setMeta(
                    new MetaItem(LABEL, Values.create("Country")),
                    new MetaItem(ABOUT, Values.create("http://project-haystack.org/tag/geoCountry"))
                )
        );
        smartHome = assetStorageService.merge(smartHome);
        smartHomeId = smartHome.getId();

        // The "Apartment 1" is the demo apartment with complex scenes
        ServerAsset apartment1 = createDemoApartment(smartHome, "Apartment 1");
        apartment1 = assetStorageService.merge(apartment1);
        apartment1Id = apartment1.getId();

        ServerAsset apartment1ServiceAgent = new ServerAsset("Service Agent (Simulator)", AGENT, apartment1);
        apartment1ServiceAgent.setAttributes(
            initProtocolConfiguration(new AssetAttribute("apartmentSimulator"), SimulatorProtocol.PROTOCOL_NAME)
                .addMeta(
                    new MetaItem(
                        SimulatorProtocol.CONFIG_MODE,
                        Values.create(SimulatorProtocol.Mode.WRITE_THROUGH_IMMEDIATE.toString())
                    ))
        );
        apartment1ServiceAgent = assetStorageService.merge(apartment1ServiceAgent);
        apartment1ServiceAgentId = apartment1ServiceAgent.getId();

        ServerAsset apartment1Livingroom = createDemoApartmentRoom(apartment1, "Living Room");
        addDemoApartmentRoomMotionSensor(apartment1Livingroom, true, () -> new MetaItem[]{
            new MetaItem(AGENT_LINK, new AttributeRef(apartment1ServiceAgentId, "apartmentSimulator").toArrayValue()),
            new MetaItem(SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(NumberSimulatorElement.ELEMENT_NAME))
        });
        addDemoApartmentRoomCO2Sensor(apartment1Livingroom, true, () -> new MetaItem[]{
            new MetaItem(AGENT_LINK, new AttributeRef(apartment1ServiceAgentId, "apartmentSimulator").toArrayValue()),
            new MetaItem(SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(NumberSimulatorElement.ELEMENT_NAME))
        });
        addDemoApartmentRoomHumiditySensor(apartment1Livingroom, true, () -> new MetaItem[]{
            new MetaItem(AGENT_LINK, new AttributeRef(apartment1ServiceAgentId, "apartmentSimulator").toArrayValue()),
            new MetaItem(SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(NumberSimulatorElement.ELEMENT_NAME))
        });
        addDemoApartmentRoomThermometer(apartment1Livingroom, true, () -> new MetaItem[]{
            new MetaItem(AGENT_LINK, new AttributeRef(apartment1ServiceAgentId, "apartmentSimulator").toArrayValue()),
            new MetaItem(SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(NumberSimulatorElement.ELEMENT_NAME))
        });
        addDemoApartmentTemperatureControl(apartment1Livingroom, true, () -> new MetaItem[]{
            new MetaItem(AGENT_LINK, new AttributeRef(apartment1ServiceAgentId, "apartmentSimulator").toArrayValue()),
            new MetaItem(SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(NumberSimulatorElement.ELEMENT_NAME))
        });

        apartment1Livingroom = assetStorageService.merge(apartment1Livingroom);
        apartment1LivingroomId = apartment1Livingroom.getId();

        ServerAsset apartment1Kitchen = createDemoApartmentRoom(apartment1, "Kitchen");
        addDemoApartmentRoomMotionSensor(apartment1Kitchen, true, () -> new MetaItem[]{
            new MetaItem(AGENT_LINK, new AttributeRef(apartment1ServiceAgentId, "apartmentSimulator").toArrayValue()),
            new MetaItem(SimulatorProtocol.SIMULATOR_ELEMENT, Values.create(NumberSimulatorElement.ELEMENT_NAME))
        });

        apartment1Kitchen = assetStorageService.merge(apartment1Kitchen);
        apartment1KitchenId = apartment1Kitchen.getId();

        if (importDemoScenes) {
            Scene[] scenes = new Scene[]{
                new Scene("homeScene", "Home scene", "HOME", "0 0 7 ? *", false, 21d),
                new Scene("awayScene", "Away scene", "AWAY", "0 30 8 ? *", true, 15d),
                new Scene("eveningScene", "Evening scene", "EVENING", "0 30 17 ? *", false, 22d),
                new Scene("nightScene", "Night scene", "NIGHT", "0 0 22 ? *", true, 19d)
            };

            ServerAsset demoApartmentSceneAgent = createDemoApartmentSceneAgent(
                apartment1, scenes, apartment1Livingroom, apartment1Kitchen
            );
            demoApartmentSceneAgent = assetStorageService.merge(demoApartmentSceneAgent);

            linkDemoApartmentWithSceneAgent(apartment1, demoApartmentSceneAgent, scenes);
            apartment1 = assetStorageService.merge(apartment1);
        }

        ServerAsset apartment2 = new ServerAsset("Apartment 2", RESIDENCE, smartHome);
        apartment2.setLocation(smartHome.getLocation());
        apartment2.setAttributes(
            new AssetAttribute("allLightsOffSwitch", AttributeType.BOOLEAN, Values.create(true))
                .setMeta(
                    new MetaItem(LABEL, Values.create("All Lights Off Switch")),
                    new MetaItem(DESCRIPTION, Values.create("When triggered, turns all lights in the apartment off")),
                    new MetaItem(RULE_EVENT, Values.create(true)),
                    new MetaItem(RULE_EVENT_EXPIRES, Values.create("10s"))
                )
        );
        apartment2 = assetStorageService.merge(apartment2);
        apartment2Id = apartment2.getId();

        /* TODO Experimental
        ServerAsset apartment2UpnpAgent= new ServerAsset("UPnP Agent", AGENT, apartment2);
        apartment2UpnpAgent.setAttributes(
            initProtocolConfiguration(new AssetAttribute("upnpConfig1"), UpnpProtocol.PROTOCOL_NAME)
        );
        apartment2UpnpAgent = assetStorageService.merge(apartment2UpnpAgent);
        */

        ServerAsset apartment2Livingroom = new ServerAsset("Living Room", ROOM, apartment2);
        apartment2Livingroom.setLocation(apartment2.getLocation());
        apartment2Livingroom.setAttributes(
            new AssetAttribute("motionSensor", AttributeType.BOOLEAN, Values.create(false))
                .setMeta(
                    new MetaItem(LABEL, Values.create("Motion Sensor")),
                    new MetaItem(DESCRIPTION, Values.create("PIR sensor that sends 'true' when motion is sensed")),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(RULE_EVENT, Values.create(true))
                ),
            new AssetAttribute("presenceDetected", AttributeType.BOOLEAN, Values.create(false))
                .setMeta(
                    new MetaItem(LABEL, Values.create("Presence Detected")),
                    new MetaItem(DESCRIPTION, Values.create("Someone is currently present in the room")),
                    new MetaItem(RULE_STATE, Values.create(true))
                ),
            new AssetAttribute("firstPresenceDetected", AttributeType.TIMESTAMP_MILLIS)
                .setMeta(
                    new MetaItem(LABEL, Values.create("First Presence Timestamp")),
                    new MetaItem(DESCRIPTION, Values.create("Timestamp of the first detected presence")),
                    new MetaItem(RULE_STATE, Values.create(true))
                ),
            new AssetAttribute("lastPresenceDetected", AttributeType.TIMESTAMP_MILLIS)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Last Presence Timestamp")),
                    new MetaItem(DESCRIPTION, Values.create("Timestamp of last detected presence")),
                    new MetaItem(RULE_STATE, Values.create(true))
                ),
            new AssetAttribute("co2Level", AttributeType.CO2_PPM, Values.create(350))
                .setMeta(
                    new MetaItem(LABEL, Values.create("CO2 Level")),
                    new MetaItem(RULE_STATE, Values.create(true))
                ),
            new AssetAttribute("lightSwitch", AttributeType.BOOLEAN, Values.create(true))
                .setMeta(
                    new MetaItem(LABEL, Values.create("Light Switch")),
                    new MetaItem(RULE_STATE, Values.create(false))
                ),
            new AssetAttribute("windowOpen", AttributeType.BOOLEAN, Values.create(false))
                .setMeta(
                    new MetaItem(LABEL, Values.create("Window Open"))
                )
        );
        apartment2Livingroom = assetStorageService.merge(apartment2Livingroom);
        apartment2LivingroomId = apartment2Livingroom.getId();

        ServerAsset apartment2Bathroom = new ServerAsset("Bathroom", ROOM, apartment2);
        apartment2Bathroom.setLocation(apartment2.getLocation());
        apartment2Bathroom.setAttributes(
            new AssetAttribute("motionSensor", AttributeType.BOOLEAN, Values.create(false))
                .setMeta(
                    new MetaItem(LABEL, Values.create("Motion Sensor")),
                    new MetaItem(DESCRIPTION, Values.create("PIR sensor that sends 'true' when motion is sensed")),
                    new MetaItem(RULE_STATE, Values.create(true)),
                    new MetaItem(RULE_EVENT, Values.create(true))
                ),
            new AssetAttribute("presenceDetected", AttributeType.BOOLEAN, Values.create(false))
                .setMeta(
                    new MetaItem(LABEL, Values.create("Presence Detected")),
                    new MetaItem(DESCRIPTION, Values.create("Someone is currently present in the room")),
                    new MetaItem(RULE_STATE, Values.create(true))
                ),
            new AssetAttribute("firstPresenceDetected", AttributeType.TIMESTAMP_MILLIS)
                .setMeta(
                    new MetaItem(LABEL, Values.create("First Presence Timestamp")),
                    new MetaItem(DESCRIPTION, Values.create("Timestamp of the first detected presence")),
                    new MetaItem(RULE_STATE, Values.create(true))
                ),
            new AssetAttribute("lastPresenceDetected", AttributeType.TIMESTAMP_MILLIS)
                .setMeta(
                    new MetaItem(LABEL, Values.create("Last Presence Timestamp")),
                    new MetaItem(DESCRIPTION, Values.create("Timestamp of last detected presence")),
                    new MetaItem(RULE_STATE, Values.create(true))
                ),
            new AssetAttribute("lightSwitch", AttributeType.BOOLEAN, Values.create(true))
                .setMeta(
                    new MetaItem(LABEL, Values.create("Light Switch")),
                    new MetaItem(RULE_STATE, Values.create(false))
                )
        );
        apartment2Bathroom = assetStorageService.merge(apartment2Bathroom);
        apartment2BathroomId = apartment2Bathroom.getId();

        ServerAsset apartment3 = new ServerAsset("Apartment 3", RESIDENCE, smartHome);
        apartment3.setLocation(smartHome.getLocation());
        apartment3 = assetStorageService.merge(apartment3);
        apartment3Id = apartment3.getId();

        ServerAsset apartment3Livingroom = new ServerAsset("Living Room", ROOM, apartment3);
        apartment3Livingroom.setLocation(apartment3.getLocation());
        apartment3Livingroom.addAttributes(
            new AssetAttribute("lightSwitch", AttributeType.BOOLEAN)
        );

        apartment3Livingroom = assetStorageService.merge(apartment3Livingroom);
        apartment3LivingroomId = apartment3Livingroom.getId();

        // ################################ Link demo users and assets ###################################

        identityService.getIdentityProvider().setRestrictedUser(keycloakDemoSetup.testuser3Id, true);
        assetStorageService.storeUserAsset(keycloakDemoSetup.testuser3Id, apartment1Id);
        assetStorageService.storeUserAsset(keycloakDemoSetup.testuser3Id, apartment1LivingroomId);
        assetStorageService.storeUserAsset(keycloakDemoSetup.testuser3Id, apartment1KitchenId);
        assetStorageService.storeUserAsset(keycloakDemoSetup.testuser3Id, apartment2Id);
    }
}