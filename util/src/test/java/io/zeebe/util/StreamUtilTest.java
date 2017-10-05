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
package io.zeebe.util;

import static io.zeebe.util.StreamUtil.readLong;
import static io.zeebe.util.StreamUtil.writeLong;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.agrona.ExpandableArrayBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

/**
 *
 */
public class StreamUtilTest
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private File file;

    @Before
    public void init() throws IOException
    {
        file = tempFolder.newFile();
    }

    @Test
    public void shouldReadAndWriteLong() throws Exception
    {
        for (int pow = 0; pow < 64; pow++)
        {
            // given
            final FileOutputStream fileOutputStream = new FileOutputStream(file);
            final long value = 1L << pow;

            // when
            writeLong(fileOutputStream, value);

            // then
            final FileInputStream fileInputStream = new FileInputStream(file);
            final long readValue = readLong(fileInputStream);
            assertThat(readValue).isEqualTo(value);
        }
    }

    @Test
    public void shouldReadStreamIntoExpandableBuffer() throws IOException
    {
        // given
        final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(2);
        final byte[] thingsToRead = new byte[] {1, 2, 3, 4};
        final InputStream stream = new ByteArrayInputStream(thingsToRead);

        // when
        StreamUtil.read(stream, buffer, 4);

        // then
        assertThat(buffer.capacity()).isGreaterThanOrEqualTo(4 +  thingsToRead.length);

        assertThat(buffer.getByte(4)).isEqualTo((byte) 1);
        assertThat(buffer.getByte(5)).isEqualTo((byte) 2);
        assertThat(buffer.getByte(6)).isEqualTo((byte) 3);
        assertThat(buffer.getByte(7)).isEqualTo((byte) 4);
    }

    @Test
    public void shouldNotReadIntoDirectBuffer() throws IOException
    {
        // given
        final ExpandableDirectByteBuffer buffer = new ExpandableDirectByteBuffer(2);
        final byte[] thingsToRead = new byte[] {1, 2, 3, 4};
        final InputStream stream = new ByteArrayInputStream(thingsToRead);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Cannot be used with direct byte buffers");

        // when
        StreamUtil.read(stream, buffer, 4);
    }
}
