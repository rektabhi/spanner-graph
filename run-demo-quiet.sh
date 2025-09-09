#!/bin/bash

# Script to run the Graph Community Detection Demo with minimal logging
echo "Running Graph Community Detection Demo with minimal logging..."

# Set logging properties to minimize output
export ORG_SLF4J_SIMPLE_LOGGER_DEFAULT_LOG_LEVEL=warn
export IO_GRPC_NETTY_SHADED_IO_GRPC_NETTY_NETTYCLIENTHANDLER_LEVEL=OFF
export IO_GRPC_NETTY_SHADED_IO_NETTY_CHANNEL_NIO_NIOEVENTLOOP_LEVEL=OFF

# Run the demo
mvn exec:java -Dexec.mainClass="com.sumo.GraphCommunityDetectionDemo" \
  -Dorg.slf4j.simpleLogger.defaultLogLevel=warn \
  -Dio.grpc.netty.shaded.io.grpc.netty.NettyClientHandler.level=OFF \
  -Dio.grpc.netty.shaded.io.netty.channel.nio.NioEventLoop.level=OFF

echo "Demo completed!"