/*
 * Copyright 2017, OpenRemote Inc.
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
package org.openremote.model.rules;

import org.openremote.model.AttributeEvent;
import org.openremote.model.Consumer;
import org.openremote.model.asset.AssetQuery;

import java.util.List;

/**
 * Facade for writing rules RHS actions, supporting asset queries within the scope
 * of the rule engine.
 */
public class Assets {

    public class RestrictedQuery extends AssetQuery<RestrictedQuery> {

        public void applyResult(Consumer<String> assetIdConsumer) {
            throw new UnsupportedOperationException("TODO Not implemented");
        }

        public void applyResults(Consumer<List<String>> assetIdListConsumer) {
            throw new UnsupportedOperationException("TODO Not implemented");
        }

    }

    public RestrictedQuery query() {
        return new RestrictedQuery();

    }

    public void dispatch(AttributeEvent event) {
        throw new UnsupportedOperationException("TODO Not implemented");
    }

}