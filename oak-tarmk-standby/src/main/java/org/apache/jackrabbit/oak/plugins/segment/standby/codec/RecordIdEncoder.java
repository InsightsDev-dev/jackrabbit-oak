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

package org.apache.jackrabbit.oak.plugins.segment.standby.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;

import org.apache.jackrabbit.oak.plugins.segment.RecordId;

@Deprecated
public class RecordIdEncoder extends MessageToByteEncoder<RecordId> {

    @Override
    @Deprecated
    protected void encode(ChannelHandlerContext ctx, RecordId msg, ByteBuf out)
            throws Exception {
        byte[] body = msg.toString().getBytes(CharsetUtil.UTF_8);
        out.writeInt(body.length + 1);
        out.writeByte(Messages.HEADER_RECORD);
        out.writeBytes(body);
    }
}
