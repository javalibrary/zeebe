/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.transport.clientapi;

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.broker.transport.clientapi.MockDispatcherFactory.dispatcherOn;
import static io.zeebe.util.StringUtil.getBytes;
import static io.zeebe.util.VarDataUtil.readBytes;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.broker.logstreams.BrokerEventMetadata;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.zeebe.protocol.clientapi.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.transport.protocol.Protocols;
import io.zeebe.transport.protocol.TransportHeaderDescriptor;
import io.zeebe.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import io.zeebe.util.buffer.DirectBufferWriter;
import org.junit.Before;
import org.junit.Test;

public class CommandResponseWriterTest
{
    public static final String TOPIC_NAME = "test-topic";
    private static final DirectBuffer TOPIC_NAME_BUFFER = wrapString(TOPIC_NAME);
    private static final int PARTITION_ID = 1;
    private static final long KEY = 2L;
    private static final byte[] EVENT = getBytes("eventType");

    private final UnsafeBuffer sendBuffer = new UnsafeBuffer(new byte[1024 * 1024]);

    private final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
    private final RequestResponseProtocolHeaderDescriptor protocolHeaderDescriptor = new RequestResponseProtocolHeaderDescriptor();
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final ExecuteCommandResponseDecoder responseDecoder = new ExecuteCommandResponseDecoder();

    private CommandResponseWriter responseWriter;

    private BrokerEventMetadata metadata;
    private DirectBufferWriter eventWriter;

    @Before
    public void setup()
    {
        metadata = new BrokerEventMetadata();
        eventWriter = new DirectBufferWriter();
    }

    @Test
    public void shouldWriteResponse()
    {
        // given
        final Dispatcher mockDispatcher = dispatcherOn(1, sendBuffer).thatDoes().claim().done();
        responseWriter = new CommandResponseWriter(mockDispatcher);

        // when
        metadata
            .reqChannelId(1)
            .reqConnectionId(2L)
            .reqRequestId(3L);

        eventWriter.wrap(new UnsafeBuffer(EVENT), 0, EVENT.length);

        responseWriter
            .topicName(TOPIC_NAME_BUFFER)
            .partitionId(PARTITION_ID)
            .key(KEY)
            .brokerEventMetadata(metadata)
            .eventWriter(eventWriter)
            .tryWriteResponse();

        // then
        int offset = DataFrameDescriptor.HEADER_LENGTH;

        transportHeaderDescriptor.wrap(sendBuffer, offset);
        assertThat(transportHeaderDescriptor.protocolId()).isEqualTo(Protocols.REQUEST_RESPONSE);

        offset += TransportHeaderDescriptor.HEADER_LENGTH;

        protocolHeaderDescriptor.wrap(sendBuffer, offset);
        assertThat(protocolHeaderDescriptor.connectionId()).isEqualTo(2L);
        assertThat(protocolHeaderDescriptor.requestId()).isEqualTo(3L);

        offset += RequestResponseProtocolHeaderDescriptor.HEADER_LENGTH;

        messageHeaderDecoder.wrap(sendBuffer, offset);
        assertThat(messageHeaderDecoder.blockLength()).isEqualTo(responseDecoder.sbeBlockLength());
        assertThat(messageHeaderDecoder.templateId()).isEqualTo(responseDecoder.sbeTemplateId());
        assertThat(messageHeaderDecoder.schemaId()).isEqualTo(responseDecoder.sbeSchemaId());
        assertThat(messageHeaderDecoder.version()).isEqualTo(responseDecoder.sbeSchemaVersion());

        offset += messageHeaderDecoder.encodedLength();

        responseDecoder.wrap(sendBuffer, offset, responseDecoder.sbeBlockLength(), responseDecoder.sbeSchemaVersion());
        assertThat(responseDecoder.topicName()).isEqualTo(TOPIC_NAME);
        assertThat(responseDecoder.partitionId()).isEqualTo(PARTITION_ID);
        assertThat(responseDecoder.key()).isEqualTo(2L);

        assertThat(responseDecoder.eventLength()).isEqualTo(EVENT.length);

        final byte[] event = readBytes(responseDecoder::getEvent, responseDecoder::eventLength);
        assertThat(event).isEqualTo(EVENT);
    }

    @Test
    public void shouldRetryClaimFragmentIfPadding()
    {
        // given
        final Dispatcher mockDispatcher = dispatcherOn(1, sendBuffer).thatDoes().padding().then().claim().done();
        responseWriter = new CommandResponseWriter(mockDispatcher);

        // when
        metadata
            .reqChannelId(1)
            .reqConnectionId(1L)
            .reqRequestId(2L);

        eventWriter.wrap(new UnsafeBuffer(EVENT), 0, EVENT.length);

        final boolean isSent = responseWriter
            .topicName(TOPIC_NAME_BUFFER)
            .partitionId(PARTITION_ID)
            .key(KEY)
            .brokerEventMetadata(metadata)
            .eventWriter(eventWriter)
            .tryWriteResponse();

        // then
        assertThat(isSent).isTrue();
    }

    @Test
    public void shouldFailIfCannotClaimFragment()
    {
        // given
        final Dispatcher mockDispatcher = dispatcherOn(1, sendBuffer).thatDoes().fail().done();
        responseWriter = new CommandResponseWriter(mockDispatcher);

        // when
        metadata
            .reqChannelId(1)
            .reqConnectionId(1L)
            .reqRequestId(2L);

        eventWriter.wrap(new UnsafeBuffer(EVENT), 0, EVENT.length);

        final boolean isSent = responseWriter
            .topicName(TOPIC_NAME_BUFFER)
            .partitionId(PARTITION_ID)
            .key(KEY)
            .brokerEventMetadata(metadata)
            .eventWriter(eventWriter)
            .tryWriteResponse();

        // then
        assertThat(isSent).isFalse();
    }

}