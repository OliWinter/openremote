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
package org.openremote.manager.server.map;

import org.openremote.container.Container;
import org.openremote.container.ContainerService;
import org.openremote.container.web.WebService;
import org.openremote.manager.server.security.ManagerIdentityService;
import org.openremote.manager.server.web.ManagerWebService;
import org.openremote.model.value.ArrayValue;
import org.openremote.model.value.ObjectValue;
import org.openremote.model.value.Values;

import javax.ws.rs.core.UriBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.openremote.container.util.MapAccess.getString;

public class MapService implements ContainerService {

    private static final Logger LOG = Logger.getLogger(MapService.class.getName());

    public static final String MAP_TILES_PATH = "MAP_TILES_PATH";
    public static final String MAP_TILES_PATH_DEFAULT = "deployment/manager/mapdata.mbtiles";
    public static final String MAP_SETTINGS_PATH = "MAP_SETTINGS_PATH";
    public static final String MAP_SETTINGS_PATH_DEFAULT = "deployment/manager/mapsettings.json";

    // Shared SQL connection is fine concurrently in SQLite
    protected Connection connection;

    protected Path mapTilesPath;
    protected Path mapSettingsPath;

    @Override
    public void init(Container container) throws Exception {
        mapTilesPath = Paths.get(getString(container.getConfig(), MAP_TILES_PATH, MAP_TILES_PATH_DEFAULT));
        if (!Files.isRegularFile(mapTilesPath)) {
            throw new IllegalStateException(
                "Map tiles data file not found (wrong working directory?): " + mapTilesPath.toAbsolutePath()
            );
        }

        mapSettingsPath = Paths.get(getString(container.getConfig(), MAP_SETTINGS_PATH, MAP_SETTINGS_PATH_DEFAULT));
        if (!Files.isRegularFile(mapSettingsPath)) {
            throw new IllegalStateException(
                "Map settings file not found: " + mapSettingsPath.toAbsolutePath()
            );
        }

        container.getService(WebService.class).getApiSingletons().add(
            new MapResourceImpl(this, container.getService(ManagerIdentityService.class))
        );
    }

    @Override
    public void start(Container container) throws Exception {
        LOG.info("Starting map service with tile data: " + mapTilesPath.toAbsolutePath());
        Class.forName(org.sqlite.JDBC.class.getName());
        connection = DriverManager.getConnection("jdbc:sqlite:" + mapTilesPath.toAbsolutePath());
    }

    @Override
    public void stop(Container container) throws Exception {
        LOG.info("Stopping map service...");
        if (connection != null) {
            connection.close();
        }
    }

    public ObjectValue getMapSettings(String realm, UriBuilder baseUriBuilder) {
        ObjectValue mapSettings;

        // Mix settings from file with database metadata, and some hardcoded magic
        try {
            String mapSettingsJson = new String(Files.readAllBytes(mapSettingsPath), "utf-8");
            mapSettings = Values.<ObjectValue>parse(mapSettingsJson).orElseThrow(() ->
                new RuntimeException("Error parsing map settings: " + mapSettingsPath.toAbsolutePath())
            );
        } catch (Exception ex) {
            throw new RuntimeException("Error parsing map settings: " + mapSettingsPath.toAbsolutePath(), ex);
        }

        ObjectValue style = mapSettings.getObject("style").orElseThrow(() ->
            new RuntimeException("Missing 'style' field in map settings style: " + mapSettingsPath.toAbsolutePath())
        );

        style.put("version", 8);

        if (style.hasKey("sprite")) {
            // Rewrite path to sprite file to our external host
            String spritePath =
                style.getString("sprite").orElseThrow(() ->
                    new RuntimeException("Missing 'sprite' field in map settings style: " + mapSettingsPath.toAbsolutePath())
                );
            String spriteUri =
                baseUriBuilder.clone()
                    .replacePath(ManagerWebService.MANAGER_PATH)
                    .path(spritePath)
                    .build().toString();
            style.put("sprite", spriteUri);
        }

        String glyphsUri = baseUriBuilder.clone()
            .replacePath(ManagerWebService.MANAGER_PATH)
            .path("/fonts/")
            .build().toString() + "{fontstack}/{range}.pbf";
        style.put("glyphs", glyphsUri);

        ObjectValue sources = Values.createObject();
        style.put("sources", sources);

        ObjectValue vectorTiles = Values.createObject();
        sources.put("vector_tiles", vectorTiles);

        vectorTiles.put("type", "vector");

        ArrayValue tilesArray = Values.createArray();
        String tileUrl = baseUriBuilder.clone().replacePath(realm).path("map/tile").build().toString() + "/{z}/{x}/{y}";
        tilesArray.set(0, tileUrl);
        vectorTiles.put("tiles", tilesArray);

        PreparedStatement query = null;
        ResultSet result = null;
        try {
            query = connection.prepareStatement("select NAME, VALUE from METADATA");
            result = query.executeQuery();

            Map<String, String> resultMap = new HashMap<>();
            while (result.next()) {
                resultMap.put(result.getString(1), result.getString(2));
            }

            if (resultMap.size() == 0) {
                throw new RuntimeException("Missing JSON metadata in map database");
            }

            ObjectValue metadataJson = Values.<ObjectValue>parse(resultMap.get("json")).orElseThrow(() ->
                new RuntimeException("Error parsing JSON metadata from map database")
            );
            ArrayValue vectorLayers = metadataJson.getArray("vector_layers").orElseThrow(() ->
                new RuntimeException("Missing 'vector_layers' field in metadata from map database")
            );
            vectorTiles.put("vector_layers", vectorLayers);
            vectorTiles.put("maxzoom", Integer.valueOf(resultMap.get("maxzoom")));
            vectorTiles.put("minzoom", Integer.valueOf(resultMap.get("minzoom")));
            vectorTiles.put("attribution", resultMap.get("attribution"));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            closeQuietly(query, result);
        }

        return mapSettings;
    }

    public byte[] getMapTile(int zoom, int column, int row) {
        // Flip y, oh why
        row = new Double(Math.pow(2, zoom) - 1 - row).intValue();

        PreparedStatement query = null;
        ResultSet result = null;
        try {
            query = connection.prepareStatement(
                "select TILE_DATA from TILES where ZOOM_LEVEL = ? and TILE_COLUMN = ? and TILE_ROW = ?"
            );

            int index = 0;
            query.setInt(++index, zoom);
            query.setInt(++index, column);
            query.setInt(++index, row);

            result = query.executeQuery();

            if (result.next()) {
                return result.getBytes(1);
            } else {
                return null;
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            closeQuietly(query, result);
        }
    }

    protected void closeQuietly(PreparedStatement query, ResultSet result) {
        try {
            if (result != null) {
                result.close();
            }
            if (query != null) {
                query.close();
            }
        } catch (Exception ex) {
            LOG.warning("Error closing query/result: " + ex);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
            "mapSettingsPath=" + mapSettingsPath +
            '}';
    }
}