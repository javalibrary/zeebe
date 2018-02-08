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
package io.zeebe.transport;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.impl.DefaultChannelFactory;
import io.zeebe.transport.impl.RemoteAddressImpl;
import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.transport.impl.TransportChannel.ChannelLifecycleListener;
import io.zeebe.transport.impl.TransportChannelFactory;
import io.zeebe.transport.util.RecordingChannelListener;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.sched.testing.ActorSchedulerRule;

public class TransportChannelListenerTest
{
    public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(3);
    public AutoCloseableRule closeables = new AutoCloseableRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(actorSchedulerRule).around(closeables);

    private static final SocketAddress ADDRESS = new SocketAddress("localhost", 51115);
    protected static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer(0, 0);

    protected ClientTransport clientTransport;
    protected ServerTransport serverTransport;

    protected CountingChannelFactory clientChannelFactory;

    @Before
    public void setUp()
    {
        final Dispatcher clientSendBuffer = Dispatchers.create("clientSendBuffer")
            .bufferSize(32 * 1024 * 1024)
            .actorScheduler(actorSchedulerRule.get())
            .build();
        closeables.manage(clientSendBuffer);

        final Dispatcher serverSendBuffer = Dispatchers.create("serverSendBuffer")
            .bufferSize(32 * 1024 * 1024)
            .actorScheduler(actorSchedulerRule.get())
            .build();
        closeables.manage(serverSendBuffer);

        clientChannelFactory = new CountingChannelFactory();

        clientTransport = Transports.newClientTransport()
            .sendBuffer(clientSendBuffer)
            .requestPoolSize(128)
            .scheduler(actorSchedulerRule.get())
            .channelFactory(clientChannelFactory)
            .build();
        closeables.manage(clientTransport);

        serverTransport = Transports.newServerTransport()
            .sendBuffer(serverSendBuffer)
            .bindAddress(ADDRESS.toInetSocketAddress())
            .scheduler(actorSchedulerRule.get())
            .build(null, null);
        closeables.manage(serverTransport);
    }

    @Test
    public void shouldInvokeRegisteredListenerOnChannelClose() throws InterruptedException
    {
        // given
        final RecordingChannelListener clientListener = new RecordingChannelListener();
        clientTransport.registerChannelListener(clientListener);

        final RecordingChannelListener serverListener = new RecordingChannelListener();
        serverTransport.registerChannelListener(serverListener);

        final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(ADDRESS);

        // opens a channel asynchronously
        clientTransport.getOutput().sendRequest(remoteAddress, new DirectBufferWriter().wrap(EMPTY_BUFFER));

        TestUtil.waitUntil(() -> !clientListener.getOpenedConnections().isEmpty());

        // when
        clientTransport.closeAllChannels().join();

        // then
        TestUtil.waitUntil(() -> !clientListener.getClosedConnections().isEmpty());
        assertThat(clientListener.getClosedConnections()).hasSize(1);
        assertThat(clientListener.getClosedConnections().get(0)).isSameAs(remoteAddress);

        TestUtil.waitUntil(() -> !serverListener.getClosedConnections().isEmpty());
        assertThat(serverListener.getClosedConnections()).hasSize(1);

    }

    @Test
    public void shouldInvokeRegisteredListenerOnChannelOpened() throws InterruptedException
    {
        // given
        final RecordingChannelListener clientListener = new RecordingChannelListener();
        clientTransport.registerChannelListener(clientListener);

        final RecordingChannelListener serverListener = new RecordingChannelListener();
        serverTransport.registerChannelListener(serverListener);

        final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(ADDRESS);

        // when
        clientTransport.getOutput().sendRequest(remoteAddress, new DirectBufferWriter().wrap(EMPTY_BUFFER));

        // then
        TestUtil.waitUntil(() -> !clientListener.getOpenedConnections().isEmpty());
        TestUtil.waitUntil(() -> !serverListener.getOpenedConnections().isEmpty());

        assertThat(clientListener.getOpenedConnections()).containsExactly(remoteAddress);
        assertThat(serverListener.getOpenedConnections()).hasSize(1);
    }

    @Test
    public void shouldDeregisterListener()
    {
        // given
        final RecordingChannelListener clientListener = new RecordingChannelListener();
        clientTransport.registerChannelListener(clientListener);

        final RecordingChannelListener serverListener = new RecordingChannelListener();
        serverTransport.registerChannelListener(serverListener);

        final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(ADDRESS);

        clientTransport.getOutput().sendRequest(remoteAddress, new DirectBufferWriter().wrap(EMPTY_BUFFER));
        TestUtil.waitUntil(() -> !clientListener.getOpenedConnections().isEmpty());

        clientTransport.removeChannelListener(clientListener);
        serverTransport.removeChannelListener(serverListener);

        // when
        clientTransport.closeAllChannels().join();

        // then

        assertThat(clientListener.getClosedConnections()).hasSize(0);
        assertThat(serverListener.getClosedConnections()).hasSize(0);
    }

    @Test
    public void shouldNotInvokeListenerWhenChannelCannotConnect() throws InterruptedException
    {
        // given
        final RecordingChannelListener clientListener = new RecordingChannelListener();
        clientTransport.registerChannelListener(clientListener);

        serverTransport.close();

        // when
        clientTransport.registerRemoteAddress(ADDRESS); // triggering connection attempts

        // then
        waitUntil(() -> clientChannelFactory.getCreatedChannels() >= 2); // first connection attempt failed
        assertThat(clientListener.getOpenedConnections()).isEmpty();
        assertThat(clientListener.getClosedConnections()).isEmpty();
    }

    protected static class CountingChannelFactory implements TransportChannelFactory
    {
        protected AtomicInteger createdChannels = new AtomicInteger();
        protected TransportChannelFactory actualFactory = new DefaultChannelFactory();

        @Override
        public TransportChannel buildClientChannel(ChannelLifecycleListener listener, RemoteAddressImpl remoteAddress,
                int maxMessageSize, FragmentHandler readHandler)
        {
            createdChannels.incrementAndGet();
            return actualFactory.buildClientChannel(listener, remoteAddress, maxMessageSize, readHandler);
        }

        public int getCreatedChannels()
        {
            return createdChannels.get();
        }

        @Override
        public TransportChannel buildServerChannel(ChannelLifecycleListener listener, RemoteAddressImpl remoteAddress,
                int maxMessageSize, FragmentHandler readHandler, SocketChannel media)
        {
            throw new UnsupportedOperationException();
        }

    }

}
