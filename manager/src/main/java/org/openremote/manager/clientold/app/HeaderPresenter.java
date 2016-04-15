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
package org.openremote.manager.clientold.app;

import com.google.gwt.place.shared.Place;
import com.google.gwt.place.shared.PlaceController;
import com.google.inject.Inject;
import org.openremote.manager.clientold.event.bus.EventBus;
import org.openremote.manager.clientold.service.SecurityService;

public class HeaderPresenter implements HeaderView.Presenter {

    private HeaderView view;
    private PlaceController placeController;
    private SecurityService securityService;

    @Inject
    public HeaderPresenter(HeaderView view,
                           SecurityService securityService,
                           PlaceController placeController,
                           EventBus eventbus) {
        this.view = view;
        this.placeController = placeController;
        this.securityService = securityService;

        view.setPresenter(this);
        view.setUsername(securityService.getUsername());
    }

    @Override
    public HeaderView getView() {
        return view;
    }

    @Override
    public void goTo(Place place) {
        placeController.goTo(place);
    }

    @Override
    public void onPlaceChange(Place place) {
        view.onPlaceChange(place);
    }

    @Override
    public void doLogout() {
        securityService.logout();
    }

    @Override
    public void setUsername(String username) {
        view.setUsername(username);
    }
}
