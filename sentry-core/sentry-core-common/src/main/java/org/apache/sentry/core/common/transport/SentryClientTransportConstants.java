/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.sentry.core.common.transport;


/**
 * Defines configuration strings needed for sentry thrift clients to handle the transport level
 * operations.
 * <p>
 * This class is abstracted by <code>SentryClientTransportConfigInterface</code>.
 * Clients that needs these configuration string use the implementations of interface
 * <code>SentryClientTransportConfigInterface</code>.
 */
class SentryClientTransportConstants {

  /**
   * max retry num for client rpc
   * {link RetryClientInvocationHandler#invokeImpl(Object, Method, Object[])}
   */
  static final String SENTRY_RPC_RETRY_TOTAL = "sentry.service.client.rpc.retry-total";
  static final int SENTRY_RPC_RETRY_TOTAL_DEFAULT = 3;

  /**
   * full retry num for getting the connection in non-pool model
   * In a full retry, it will cycle through all available sentry servers
   */
  static final String SENTRY_FULL_RETRY_TOTAL =
    "sentry.service.client.connection.full.retry-total";
  static final int SENTRY_FULL_RETRY_TOTAL_DEFAULT = 2;

  static final int RPC_PORT_DEFAULT = 8038;

  /**
   * Defines configuration strings needed for sentry thrift policy clients to handle
   * the transport level operations.
   */
  static class PolicyClientConstants {
    //configuration for server port
    static final String SERVER_RPC_PORT = "sentry.service.client.server.rpc-port";

    //configuration for server address. It can be coma seperated list of server addresses.
    static final String SERVER_RPC_ADDRESS = "sentry.service.client.server.rpc-address";


    /**
     * This configuration parameter is only meant to be used for testing purposes.
     */
    static final String SECURITY_MODE = "sentry.service.security.mode";

    /**
     * full retry num for getting the connection in non-pool model
     * In a full retry, it will cycle through all available sentry servers
     */
    static final String SENTRY_FULL_RETRY_TOTAL =
      SentryClientTransportConstants.SENTRY_FULL_RETRY_TOTAL;
    static final int SENTRY_FULL_RETRY_TOTAL_DEFAULT =
      SentryClientTransportConstants.SENTRY_FULL_RETRY_TOTAL_DEFAULT;

    static final String SECURITY_USE_UGI_TRANSPORT = "sentry.service.security.use.ugi";
    static final String PRINCIPAL = "sentry.service.server.principal";

    //configration for the client connection timeout.
    static final String SERVER_RPC_CONN_TIMEOUT =
      "sentry.service.client.server.rpc-connection-timeout";

    static final int SERVER_RPC_CONN_TIMEOUT_DEFAULT = 200000;

    /**
     * max retry num for client rpc
     * {link RetryClientInvocationHandler#invokeImpl(Object, Method, Object[])}
     */
    static final String SENTRY_RPC_RETRY_TOTAL = "sentry.service.client.rpc.retry-total";
    static final int SENTRY_RPC_RETRY_TOTAL_DEFAULT = 3;

    // commons-pool configuration for pool size
    static final String SENTRY_POOL_MAX_TOTAL = "sentry.service.client.connection.pool.max-total";
    static final int SENTRY_POOL_MAX_TOTAL_DEFAULT = 8;
    static final String SENTRY_POOL_MAX_IDLE = "sentry.service.client.connection.pool.max-idle";
    static final int SENTRY_POOL_MAX_IDLE_DEFAULT = 8;
    static final String SENTRY_POOL_MIN_IDLE = "sentry.service.client.connection.pool.min-idle";
    static final int SENTRY_POOL_MIN_IDLE_DEFAULT = 0;

    // retry num for getting the connection from connection pool
    static final String SENTRY_POOL_RETRY_TOTAL =
      SentryClientTransportConstants.SENTRY_FULL_RETRY_TOTAL;
    static final int SENTRY_POOL_RETRY_TOTAL_DEFAULT =
      SentryClientTransportConstants.SENTRY_RPC_RETRY_TOTAL_DEFAULT;

  }

  /**
   * Defines configuration strings needed for sentry HDFS clients to handle the transport level
   * operations.
   */
  static class HDFSClientConstants {

    //Default server port
    static final int SERVER_RPC_PORT_DEFAULT = SentryClientTransportConstants.RPC_PORT_DEFAULT;

    //configuration for server port
    static final String SERVER_RPC_PORT = "sentry.hdfs.service.client.server.rpc-port";

    //configuration for server address. It can be coma seperated list of server addresses.
    static final String SERVER_RPC_ADDRESS = "sentry.hdfs.service.client.server.rpc-address";

    /**
     * This configuration parameter is only meant to be used for testing purposes.
     */
    static final String SECURITY_MODE = "sentry.hdfs.service.security.mode";

    /**
     * full retry num for getting the connection in non-pool model
     * In a full retry, it will cycle through all available sentry servers
     */
    static final String SENTRY_FULL_RETRY_TOTAL =
      SentryClientTransportConstants.SENTRY_FULL_RETRY_TOTAL;

    static final int SENTRY_FULL_RETRY_TOTAL_DEFAULT =
      SentryClientTransportConstants.SENTRY_FULL_RETRY_TOTAL_DEFAULT;

    static final String SECURITY_USE_UGI_TRANSPORT = "sentry.hdfs.service.security.use.ugi";

    static final String PRINCIPAL = "sentry.hdfs.service.server.principal";

    static final String RPC_ADDRESS = "sentry.hdfs.service.client.server.rpc-address";

    static final String RPC_ADDRESS_DEFAULT = "0.0.0.0"; //NOPMD

    //configration for the client connection timeout.
    static final String SERVER_RPC_CONN_TIMEOUT =
      "sentry.hdfs.service.client.server.rpc-connection-timeout";

    static final int SERVER_RPC_CONN_TIMEOUT_DEFAULT = 200000;

    /**
     * max retry num for client rpc
     * {link RetryClientInvocationHandler#invokeImpl(Object, Method, Object[])}
     */
    static final String SENTRY_RPC_RETRY_TOTAL =
      SentryClientTransportConstants.SENTRY_RPC_RETRY_TOTAL;

    static final int SENTRY_RPC_RETRY_TOTAL_DEFAULT = 3;
  }
}
