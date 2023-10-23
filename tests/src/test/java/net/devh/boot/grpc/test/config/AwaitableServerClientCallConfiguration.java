/*
 * Copyright (c) 2016-2023 The gRPC-Spring Authors
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

package net.devh.boot.grpc.test.config;

import static net.devh.boot.grpc.common.util.InterceptorOrder.ORDER_FIRST;
import static net.devh.boot.grpc.common.util.InterceptorOrder.ORDER_LAST;

import java.util.concurrent.CountDownLatch;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

/**
 * Helper configuration that can be used to await the completion/closing of the next calls.
 *
 * @author Daniel Theuke (daniel.theuke@aequitas-software.de)
 */
@Configuration
public class AwaitableServerClientCallConfiguration {

    private static CountDownLatch serverCounter;
    private static CountDownLatch clientCounter;

    /**
     * A testing server interceptor, that allows awaiting the completion of the server call that is otherwise closed
     * asynchronously.
     *
     * @return A testing server interceptor bean.
     */
    @GrpcGlobalServerInterceptor
    @Order(ORDER_FIRST)
    ServerInterceptor awaitableServerInterceptor() {
        return new ServerInterceptor() {

            @Override
            public <ReqT, RespT> Listener<ReqT> interceptCall(
                    final ServerCall<ReqT, RespT> call,
                    final Metadata headers,
                    final ServerCallHandler<ReqT, RespT> next) {

                if (serverCounter == null || serverCounter.getCount() == 0) {
                    return next.startCall(call, headers);
                } else {
                    final CountDownLatch thatCounter = serverCounter;
                    return new SimpleForwardingServerCallListener<ReqT>(next.startCall(call, headers)) {

                        @Override
                        public void onComplete() {
                            super.onComplete();
                            thatCounter.countDown();
                        }

                        @Override
                        public void onCancel() {
                            super.onCancel();
                            thatCounter.countDown();
                        }

                    };
                }
            }

        };
    }

    /**
     * A testing client interceptor, that allows awaiting the completion of the client call that is otherwise closed
     * asynchronously.
     *
     * @return A testing client interceptor bean.
     */
    @GrpcGlobalClientInterceptor
    @Order(ORDER_LAST)
    ClientInterceptor awaitableClientInterceptor() {
        return new ClientInterceptor() {

            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    final MethodDescriptor<ReqT, RespT> method,
                    final CallOptions callOptions,
                    final Channel next) {

                if (clientCounter == null || clientCounter.getCount() == 0) {
                    return next.newCall(method, callOptions);
                } else {
                    final CountDownLatch thatCounter = clientCounter;
                    return new SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {

                        @Override
                        public void start(final Listener<RespT> responseListener, final Metadata headers) {
                            super.start(new SimpleForwardingClientCallListener<RespT>(responseListener) {

                                @Override
                                public void onClose(final Status status, final Metadata trailers) {
                                    super.onClose(status, trailers);
                                    thatCounter.countDown();
                                }

                            }, headers);
                        }

                    };
                }
            }

        };
    }

    /**
     * Returns a {@link CountDownLatch} that will be used in the next server calls and can be used to await the
     * {@link ServerCall#close(Status, Metadata) ServerCall close}. This method must be called before the call is
     * started.
     *
     * @param count The number of call closes to await.
     * @return The counter used to await the server call close.
     */
    public static CountDownLatch awaitNextServerCallCloses(final int count) {
        final CountDownLatch newCounter = new CountDownLatch(count);
        serverCounter = newCounter;
        return newCounter;
    }

    /**
     * Returns a {@link CountDownLatch} that will be used in the next client calls and can be used to await the
     * {@link io.grpc.ClientCall.Listener#onClose(Status, Metadata) ClientCall close}. This method must be called before
     * the call is started.
     *
     * @param count The number of call closes to await.
     * @return The counter used to await the client call close.
     */
    public static CountDownLatch awaitNextClientCallCloses(final int count) {
        final CountDownLatch newCounter = new CountDownLatch(count);
        clientCounter = newCounter;
        return newCounter;
    }

    /**
     * Returns a {@link CountDownLatch} that will be used in the next server and client calls and can be used to await
     * the respective closes. This method must be called before the call is started.
     *
     * @param count The number of call closes to await.
     * @return The counter used to await the client call close.
     * @see #awaitNextClientCallCloses(int)
     * @see #awaitNextServerCallCloses(int)
     */
    public static CountDownLatch awaitNextServerAndClientCallCloses(final int count) {
        final CountDownLatch newCounter = new CountDownLatch(2 * count);
        serverCounter = newCounter;
        clientCounter = newCounter;
        return newCounter;
    }

}
