/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.jackrabbit.oak.segment.standby;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Random;

import com.google.common.io.ByteStreams;
import org.apache.jackrabbit.oak.api.Blob;
import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.segment.SegmentNodeStoreBuilders;
import org.apache.jackrabbit.oak.segment.file.FileStore;
import org.apache.jackrabbit.oak.segment.standby.client.StandbyClientSync;
import org.apache.jackrabbit.oak.segment.standby.server.StandbyServerSync;
import org.apache.jackrabbit.oak.segment.test.proxy.NetworkErrorProxy;
import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.commit.EmptyHook;
import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeStore;
import org.junit.Test;

public abstract class DataStoreTestBase extends TestBase {

    private static final int MB = 1024 * 1024;

    abstract FileStore getPrimary();

    abstract FileStore getSecondary();

    abstract boolean storesShouldBeEqual();

    protected byte[] addTestContent(NodeStore store, String child, int size)
            throws CommitFailedException, IOException {
        NodeBuilder builder = store.getRoot().builder();
        builder.child(child).setProperty("ts", System.currentTimeMillis());

        byte[] data = new byte[size];
        new Random().nextBytes(data);
        Blob blob = store.createBlob(new ByteArrayInputStream(data));

        builder.child(child).setProperty("testBlob", blob);

        store.merge(builder, EmptyHook.INSTANCE, CommitInfo.EMPTY);
        return data;
    }

    @Test
    public void testSync() throws Exception {
        final int blobSize = 5 * MB;
        FileStore primary = getPrimary();
        FileStore secondary = getSecondary();

        NodeStore store = SegmentNodeStoreBuilders.builder(primary).build();
        byte[] data = addTestContent(store, "server", blobSize);
        try (
                StandbyServerSync serverSync = new StandbyServerSync(getServerPort(), primary);
                StandbyClientSync cl = newStandbyClientSync(secondary)
        ) {
            serverSync.start();
            primary.flush();
            cl.run();
            assertEquals(primary.getHead(), secondary.getHead());
        }

        assertTrue(primary.getStats().getApproximateSize() < MB);
        assertTrue(secondary.getStats().getApproximateSize() < MB);

        PropertyState ps = secondary.getHead().getChildNode("root")
                .getChildNode("server").getProperty("testBlob");
        assertNotNull(ps);
        assertEquals(Type.BINARY.tag(), ps.getType().tag());
        Blob b = ps.getValue(Type.BINARY);
        assertEquals(blobSize, b.length());
        byte[] testData = new byte[blobSize];
        ByteStreams.readFully(b.getNewStream(), testData);
        assertArrayEquals(data, testData);
    }

    /*
     * See OAK-4969.
     */
    @Test
    public void testSyncUpdatedBinaryProperty() throws Exception {
        final int blobSize = 5 * MB;

        FileStore primary = getPrimary();
        FileStore secondary = getSecondary();

        NodeStore store = SegmentNodeStoreBuilders.builder(primary).build();
        try (
                StandbyServerSync serverSync = new StandbyServerSync(getServerPort(), primary);
                StandbyClientSync clientSync = newStandbyClientSync(secondary)
        ) {
            serverSync.start();

            addTestContent(store, "server", blobSize);
            primary.flush();
            clientSync.run();
            assertEquals(primary.getHead(), secondary.getHead());

            addTestContent(store, "server", blobSize);
            primary.flush();
            clientSync.run();
            assertEquals(primary.getHead(), secondary.getHead());
        }
    }

    @Test
    public void testProxySkippedBytes() throws Exception {
        useProxy(100, 1, -1, false);
    }

    @Test
    public void testProxySkippedBytesIntermediateChange() throws Exception {
        useProxy(100, 1, -1, true);
    }

    @Test
    public void testProxyFlippedStartByte() throws Exception {
        useProxy(0, 0, 0, false);
    }

    @Test
    public void testProxyFlippedIntermediateByte() throws Exception {
        useProxy(0, 0, 150, false);
    }

    @Test
    public void testProxyFlippedIntermediateByte2() throws Exception {
        useProxy(0, 0, 150000, false);
    }

    @Test
    public void testProxyFlippedIntermediateByteChange() throws Exception {
        useProxy(0, 0, 150, true);
    }

    @Test
    public void testProxyFlippedIntermediateByteChange2() throws Exception {
        useProxy(0, 0, 150000, true);
    }

    private void useProxy(int skipPosition, int skipBytes, int flipPosition, boolean intermediateChange) throws Exception {
        int blobSize = 5 * MB;
        FileStore primary = getPrimary();
        FileStore secondary = getSecondary();

        NodeStore store = SegmentNodeStoreBuilders.builder(primary).build();
        byte[] data = addTestContent(store, "server", blobSize);
        try (
                NetworkErrorProxy p = new NetworkErrorProxy(getProxyPort(), getServerHost(), getServerPort());
                StandbyServerSync serverSync = new StandbyServerSync(getServerPort(), primary);
                StandbyClientSync clientSync = newStandbyClientSync(secondary, getProxyPort())
        ) {
            p.skipBytes(skipPosition, skipBytes);
            p.flipByte(flipPosition);
            p.connect();

            serverSync.start();
            primary.flush();

            clientSync.run();

            if (skipBytes > 0 || flipPosition >= 0) {
                if (!storesShouldBeEqual()) {
                    assertFalse("stores are not expected to be equal", primary.getHead().equals(secondary.getHead()));
                }
                p.reset();
                if (intermediateChange) {
                    blobSize = 2 * MB;
                    data = addTestContent(store, "server", blobSize);
                    primary.flush();
                }
                clientSync.run();
            }
            assertEquals(primary.getHead(), secondary.getHead());
        }

        assertTrue(primary.getStats().getApproximateSize() < MB);
        assertTrue(secondary.getStats().getApproximateSize() < MB);

        PropertyState ps = secondary.getHead().getChildNode("root")
                .getChildNode("server").getProperty("testBlob");
        assertNotNull(ps);
        assertEquals(Type.BINARY.tag(), ps.getType().tag());
        Blob b = ps.getValue(Type.BINARY);
        assertEquals(blobSize, b.length());
        byte[] testData = new byte[blobSize];
        ByteStreams.readFully(b.getNewStream(), testData);
        assertArrayEquals(data, testData);
    }
}
