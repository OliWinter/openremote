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
package org.openremote.manager.server.asset;

import org.openremote.container.message.MessageBrokerService;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.server.security.ManagerIdentityService;
import org.openremote.manager.server.web.ManagerWebResource;
import org.openremote.manager.shared.asset.AssetProcessingException;
import org.openremote.manager.shared.asset.AssetResource;
import org.openremote.manager.shared.http.RequestParams;
import org.openremote.manager.shared.security.Tenant;
import org.openremote.model.Constants;
import org.openremote.model.asset.AbstractAssetQuery;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetAttribute;
import org.openremote.model.asset.AssetQuery;
import org.openremote.model.attribute.AttributeEvent;
import org.openremote.model.attribute.AttributeRef;
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.util.TextUtil;
import org.openremote.model.value.Value;
import org.openremote.model.value.ValueException;
import org.openremote.model.value.Values;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.logging.Logger;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.openremote.model.asset.AssetMeta.PROTECTED;
import static org.openremote.model.attribute.AttributeEvent.Source.CLIENT;
import static org.openremote.model.util.TextUtil.isNullOrEmpty;

public class AssetResourceImpl extends ManagerWebResource implements AssetResource {

    private static final Logger LOG = Logger.getLogger(AssetResourceImpl.class.getName());

    protected final static Asset[] EMPTY_ASSETS = new Asset[0];
    protected final AssetStorageService assetStorageService;
    protected final MessageBrokerService messageBrokerService;

    public AssetResourceImpl(TimerService timerService,
                             ManagerIdentityService identityService,
                             AssetStorageService assetStorageService,
                             MessageBrokerService messageBrokerService) {
        super(timerService, identityService);
        this.assetStorageService = assetStorageService;
        this.messageBrokerService = messageBrokerService;
    }

    @Override
    public Asset[] getCurrentUserAssets(RequestParams requestParams) {
        try {
            if (isSuperUser()) {
                return new Asset[0];
            }

            if (!isRestrictedUser()) {
                List<ServerAsset> result = assetStorageService.findAll(
                    new AssetQuery()
                        .parent(new AbstractAssetQuery.ParentPredicate(true))
                        .tenant(new AbstractAssetQuery.TenantPredicate().realm(getAuthenticatedRealm()))
                );
                return result.toArray(new Asset[result.size()]);
            }

            List<ServerAsset> assets = assetStorageService.findAll(
                new AssetQuery().select(new AbstractAssetQuery.Select(AbstractAssetQuery.Include.ALL_EXCEPT_PATH_AND_ATTRIBUTES, true)).userId(getUserId())
            );

            // Filter assets that might have been moved into a different realm and can no longer be accessed by user
            // TODO: Should we forbid moving assets between realms?
            Tenant authenticatedTenant = getAuthenticatedTenant();
            Iterator<ServerAsset> it = assets.iterator();
            while (it.hasNext()) {
                ServerAsset asset = it.next();
                if (!asset.getRealmId().equals(authenticatedTenant.getId())) {
                    LOG.warning("User '" + getUsername() + "' linked to asset in other realm, skipping: " + asset);
                    it.remove();
                }
            }
            return assets.toArray(new ServerAsset[assets.size()]);
        } catch (IllegalStateException ex) {
            throw new WebApplicationException(ex, Response.Status.BAD_REQUEST);
        }
    }

    @Override
    public Asset get(RequestParams requestParams, String assetId) {
        try {
            ServerAsset asset;

            // Check restricted
            if (isRestrictedUser()) {
                if (!assetStorageService.isUserAsset(getUserId(), assetId)) {
                    throw new WebApplicationException(Response.Status.FORBIDDEN);
                }
                asset = assetStorageService.find(assetId, true, true);
            } else {
                asset = assetStorageService.find(assetId, true);
            }

            if (asset == null)
                throw new WebApplicationException(NOT_FOUND);

            if (!isTenantActiveAndAccessible(asset)) {
                LOG.fine("Forbidden access for user '" + getUsername() + "': " + asset);
                throw new WebApplicationException(Response.Status.FORBIDDEN);
            }

            return asset;

        } catch (IllegalStateException ex) {
            throw new WebApplicationException(ex, Response.Status.BAD_REQUEST);
        }
    }

    @Override
    public void update(RequestParams requestParams, String assetId, Asset asset) {
        try {
            ServerAsset serverAsset;
            // Check restricted
            if (isRestrictedUser()) {
                if (!assetStorageService.isUserAsset(getUserId(), assetId)) {
                    throw new WebApplicationException(Response.Status.FORBIDDEN);
                }

                serverAsset = assetStorageService.find(assetId, true);

                if (serverAsset == null)
                    throw new WebApplicationException(NOT_FOUND);

                //Restricted users don't have permission to update an asset to become a root asset
                if (TextUtil.isNullOrEmpty(asset.getRealmId())) {
                    throw new WebApplicationException("RealmId is missing", BAD_REQUEST);
                }

                if (TextUtil.isNullOrEmpty(asset.getParentId())) {
                    throw new WebApplicationException("ParentId is missing", BAD_REQUEST);
                }

                //Add/Update check
                for (AssetAttribute updatedAttribute : asset.getAttributesList()) {
                    //Restricted users may only add protected attributes
                    if (!updatedAttribute.isProtected()) {
                        updatedAttribute.addMeta(new MetaItem(PROTECTED, Values.create(true)));
                    }
                    String updatedAttributeName = updatedAttribute
                        .getName()
                        .orElseThrow(() -> new WebApplicationException("No name supplied for attribute(s)", BAD_REQUEST));
                    Optional<AssetAttribute> serverAttribute = serverAsset.getAttribute(updatedAttributeName);

                    //Check if attribute is present on the asset in storage
                    if (serverAttribute.isPresent()) {
                        AssetAttribute attr = serverAttribute.get();

                        //TODO remove readonly check
                        //attr is not protected -> CONFLICT attribute already present(private)
                        //attr is protected:
                        //-can set value
                        //-can update metaItems -> user can add/update/delete items that are restricted write else BAD REQUEST
                        // merge the metaItems if they are already present
                        // if restricted read client should have received the metaItem, list those metaItems
                        // and restricted write, then the metaItem can be changed, if not, then it can't be updated
                        // Invert if else
                        if (attr.isProtected()) {
                            //If attribute isn't protected, then update

                            attr.getMeta().stream().filter(metaItem -> metaItem.isProtectedRead()).forEach(metaItem -> {
                                if(!metaItem.isProtectedWrite()) {
                                    throw new WebApplicationException("MetaItems should be protected write", BAD_REQUEST);
                                }
                            });
                            serverAsset.replaceAttribute(updatedAttribute);
                        } else {
                            throw new WebApplicationException("Attribute is already present as private", Response.Status.CONFLICT);
                        }
                    } else {
                        //If not present, then add the attribute
//                        for(MetaItem item : updatedAttribute.getMeta()) {
//                            if(!item.isProtectedWrite()) {
//                                throw new WebApplicationException("MetaItems should be protected write", BAD_REQUEST);
//                            }
//                        }
                        serverAsset.addAttributes(updatedAttribute);
                    }
                }
                //Removal check
                for (AssetAttribute serverAttribute : serverAsset.getAttributesList()) {
                    //Check if asset is missing attributes
                    if (serverAttribute.getName().isPresent() && !asset.hasAttribute(serverAttribute.getName().get())) {
                        if (serverAttribute.isProtected()) {
                            //If attribute isn't protected and not readonly, then remove
                            serverAsset.removeAttribute(serverAttribute.getName().get());
                        } else {
                            throw new WebApplicationException(String.format("No permission to remove attribute %s", serverAttribute.getName().get()), Response.Status.CONFLICT);
                        }
                    }
                }
            } else {
                serverAsset = assetStorageService.find(assetId, true);

                if (serverAsset == null)
                    throw new WebApplicationException(NOT_FOUND);
            }

            //TODO move checks to optimise flow. Check if it could be easier
            Tenant tenant = identityService.getIdentityProvider().getTenantForRealmId(asset.getRealmId());
            if (tenant == null)
                throw new WebApplicationException(BAD_REQUEST);

            // Check old realm, must be accessible
            if (!isTenantActiveAndAccessible(tenant)) {
                LOG.fine("Forbidden access for user '" + getUsername() + "', can't update: " + serverAsset);
                throw new WebApplicationException(Response.Status.FORBIDDEN);
            }

            ServerAsset updatedAsset;
            // When restricted user the asset is mapped manually
            if (!isRestrictedUser()) {
                // Map into server-side asset, do not allow to change the type
                updatedAsset = ServerAsset.map(asset, serverAsset, null, serverAsset.getType(), null);
            } else {
                updatedAsset = serverAsset;
            }

            // Check new realm
            if (!isTenantActiveAndAccessible(updatedAsset)) {
                LOG.fine("Forbidden access for user '" + getUsername() + "', can't update: " + serverAsset);
                throw new WebApplicationException(Response.Status.FORBIDDEN);
            }

            assetStorageService.merge(updatedAsset, isRestrictedUser() ? getUsername() : null);

        } catch (IllegalStateException ex) {
            throw new WebApplicationException(ex, Response.Status.BAD_REQUEST);
        }
    }

    @Override
    public void writeAttributeValue(RequestParams requestParams, String assetId, String attributeName, String rawJson) {
        try {
            try {
                Value value = Values.instance()
                    .parse(rawJson)
                    .orElse(null); // When parsing literal JSON "null"

                AttributeEvent event = new AttributeEvent(
                    new AttributeRef(assetId, attributeName), value, timerService.getCurrentTimeMillis()
                );

                // Process asynchronously but block for a little while waiting for the result
                Map<String, Object> headers = new HashMap<>();
                headers.put(AttributeEvent.HEADER_SOURCE, CLIENT);
                headers.put(Constants.AUTH_CONTEXT, getAuthContext());
                Object result = messageBrokerService.getProducerTemplate().requestBodyAndHeaders(
                    AssetProcessingService.ASSET_QUEUE, event, headers
                );

                if (result instanceof AssetProcessingException) {
                    AssetProcessingException processingException = (AssetProcessingException) result;
                    switch (processingException.getReason()) {
                        case ILLEGAL_SOURCE:
                        case NO_AUTH_CONTEXT:
                        case INSUFFICIENT_ACCESS:
                            throw new WebApplicationException(Response.Status.FORBIDDEN);
                        case ASSET_NOT_FOUND:
                        case ATTRIBUTE_NOT_FOUND:
                            throw new WebApplicationException(NOT_FOUND);
                        case INVALID_AGENT_LINK:
                        case ILLEGAL_AGENT_UPDATE:
                        case INVALID_ATTRIBUTE_EXECUTE_STATUS:
                            throw new IllegalStateException(processingException);
                        default:
                            throw processingException;
                    }
                }

            } catch (ValueException ex) {
                throw new IllegalStateException("Error parsing JSON", ex);
            }

        } catch (IllegalStateException ex) {
            throw new WebApplicationException(ex, Response.Status.BAD_REQUEST);
        }
    }

    @Override
    public Asset create(RequestParams requestParams, Asset asset) {
        try {
            if (isRestrictedUser()) {
                throw new WebApplicationException(Response.Status.FORBIDDEN);
            }

            // If there was no realm provided (create was called by regular user in manager UI), use the auth realm
            if (asset.getRealmId() == null || asset.getRealmId().length() == 0) {
                asset.setRealmId(getAuthenticatedTenant().getId());
            }

            if (!isTenantActiveAndAccessible(asset)) {
                LOG.fine("Forbidden access for user '" + getUsername() + "', can't create: " + asset);
                throw new WebApplicationException(Response.Status.FORBIDDEN);
            }

            ServerAsset serverAsset = ServerAsset.map(asset, new ServerAsset());

            // Allow client to set identifier
            // TODO Instead should return asset identifier instead of NO CONTENT
            if (asset.getId() != null) {
                // At least some sanity check, we must hope that the client has set a unique ID
                if (asset.getId().length() < 22) {
                    LOG.fine("Identifier value is too short, can't persist asset: " + asset);
                    throw new WebApplicationException(BAD_REQUEST);
                }
                serverAsset.setId(asset.getId());
            }

            return assetStorageService.merge(serverAsset);

        } catch (IllegalStateException ex) {
            throw new WebApplicationException(ex, Response.Status.BAD_REQUEST);
        }
    }

    @Override
    public void delete(RequestParams requestParams, String assetId) {
        try {
            if (isRestrictedUser()) {
                throw new WebApplicationException(Response.Status.FORBIDDEN);
            }
            ServerAsset asset = assetStorageService.find(assetId, true, false);
            if (asset == null)
                return;

            if (!isTenantActiveAndAccessible(asset)) {
                LOG.fine("Forbidden access for user '" + getUsername() + "', can't delete: " + asset);
                throw new WebApplicationException(Response.Status.FORBIDDEN);
            }
            if (!assetStorageService.delete(assetId)) {
                throw new WebApplicationException(BAD_REQUEST);
            }
        } catch (IllegalStateException ex) {
            throw new WebApplicationException(ex, Response.Status.BAD_REQUEST);
        }
    }

    @Override
    public Asset[] queryAssets(RequestParams requestParams, AbstractAssetQuery query) {
        try {
            if (query == null) {
                return EMPTY_ASSETS;
            }

            if (isRestrictedUser()) {
                query = query.userId(getUserId());
            }

            Tenant tenant = query.tenantPredicate != null
                ? !isNullOrEmpty(query.tenantPredicate.realmId)
                ? identityService.getIdentityProvider().getTenantForRealmId(query.tenantPredicate.realmId)
                : !isNullOrEmpty(query.tenantPredicate.realm)
                ? identityService.getIdentityProvider().getTenantForRealm(query.tenantPredicate.realm)
                : getAuthenticatedTenant()
                : getAuthenticatedTenant();

            if (tenant == null) {
                throw new WebApplicationException(NOT_FOUND);
            }

            if (!isTenantActiveAndAccessible(tenant)) {
                return EMPTY_ASSETS;
            }

            // This replicates behaviour of old getRoot and getChildren methods
            if (!isSuperUser() || query.parentPredicate == null || query.parentPredicate.noParent) {
                query.tenant(new AbstractAssetQuery.TenantPredicate(tenant.getId()));
            }

            List<ServerAsset> result = assetStorageService.findAll(query);
            return result.toArray(new Asset[result.size()]);

        } catch (IllegalStateException ex) {
            throw new WebApplicationException(ex, Response.Status.BAD_REQUEST);
        }
    }
}
