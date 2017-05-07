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
package org.openremote.container.timer;

import org.openremote.container.Container;
import org.openremote.container.ContainerService;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.openremote.container.util.MapAccess.getString;

/**
 * Wall real clock timer or pseudo clock time (for testing).
 */
public class TimerService implements ContainerService {

    public static final String TIMER_CLOCK_TYPE = "TIMER_CLOCK_TYPE";
    public static final String TIMER_CLOCK_TYPE_DEFAULT = Clock.REAL.toString();

    public enum Clock {
        REAL {
            @Override
            public long getCurrentTimeMillis() {
                return System.currentTimeMillis();
            }

            @Override
            public long advanceTime(long amount, TimeUnit unit) {
                throw new UnsupportedOperationException("Wall clock can not be advanced manually");
            }
        },
        PSEUDO {
            AtomicLong timer = new AtomicLong(System.currentTimeMillis()); // Init to wall clock time!

            @Override
            public long getCurrentTimeMillis() {
                return timer.get();
            }

            @Override
            public long advanceTime(long amount, TimeUnit unit) {
                return timer.addAndGet(unit.toMillis(amount));
            }
        };

        public abstract long getCurrentTimeMillis();
        public abstract long advanceTime(long amount, TimeUnit unit);
    }

    protected Clock clock;

    @Override
    public void init(Container container) throws Exception {
        this.clock = Clock.valueOf(
            getString(container.getConfig(), TIMER_CLOCK_TYPE, TIMER_CLOCK_TYPE_DEFAULT)
        );
    }

    @Override
    public void start(Container container) throws Exception {

    }

    @Override
    public void stop(Container container) throws Exception {

    }

    public Clock getClock() {
        return clock;
    }

    public long getCurrentTimeMillis() {
        return getClock().getCurrentTimeMillis();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
            "clock=" + clock +
            '}';
    }
}