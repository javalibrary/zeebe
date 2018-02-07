/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.util.sched;

public class ActorClock
{
    long timeMillis;

    long nanoTime;

    long nanoTimeOfLastMilli;

    long nanosSinceLastMilli;

    public boolean update()
    {
        boolean isNextTick = false;

        updateNanos();

        if (nanosSinceLastMilli >= 1_000_000)
        {
            timeMillis = System.currentTimeMillis();
            nanoTimeOfLastMilli = nanoTime;

            isNextTick = true;
        }

        return isNextTick;
    }

    public void updateNanos()
    {
        nanoTime = System.nanoTime();
        nanosSinceLastMilli = nanoTime - nanoTimeOfLastMilli;
    }

    public long getTimeMillis()
    {
        return timeMillis;
    }

    public long getNanosSinceLastMillisecond()
    {
        return nanosSinceLastMilli;
    }

    public long getNanoTime()
    {
        return nanoTime;
    }

    public static ActorClock current()
    {
        final ActorTaskRunner current = ActorTaskRunner.current();
        if (current == null)
        {
            throw new UnsupportedOperationException("ActorClock.current() can only be called from actor thread.");
        }

        return current.getClock();
    }

    public static long currentTimeMillis()
    {
        return current().getTimeMillis();
    }

}