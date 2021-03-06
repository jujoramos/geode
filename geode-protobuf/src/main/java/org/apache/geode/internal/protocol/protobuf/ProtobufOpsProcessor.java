/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.internal.protocol.protobuf;

import org.apache.logging.log4j.Logger;

import org.apache.geode.annotations.Experimental;
import org.apache.geode.internal.cache.tier.sockets.MessageExecutionContext;
import org.apache.geode.internal.exception.InvalidExecutionContextException;
import org.apache.geode.internal.logging.LogService;
import org.apache.geode.internal.protocol.protobuf.registry.OperationContextRegistry;
import org.apache.geode.internal.protocol.protobuf.statistics.ProtobufClientStatistics;
import org.apache.geode.internal.protocol.protobuf.utilities.ProtobufResponseUtilities;
import org.apache.geode.internal.serialization.SerializationService;

import static org.apache.geode.internal.protocol.protobuf.ProtocolErrorCode.*;

/**
 * This handles protobuf requests by determining the operation type of the request and dispatching
 * it to the appropriate handler.
 */
@Experimental
public class ProtobufOpsProcessor {

  private final OperationContextRegistry operationContextRegistry;
  private final SerializationService serializationService;
  private static final Logger logger = LogService.getLogger(ProtobufOpsProcessor.class);

  public ProtobufOpsProcessor(SerializationService serializationService,
      OperationContextRegistry operationContextRegistry) {
    this.serializationService = serializationService;
    this.operationContextRegistry = operationContextRegistry;
  }

  public ClientProtocol.Response process(ClientProtocol.Request request,
      MessageExecutionContext context) {
    ClientProtocol.Request.RequestAPICase requestType = request.getRequestAPICase();
    logger.debug("Processing request of type {}", requestType);
    OperationContext operationContext = operationContextRegistry.getOperationContext(requestType);
    ClientProtocol.Response.Builder builder;
    Result result;
    try {
      if (context.getAuthorizer().authorize(context.getSubject(),
          operationContext.getAccessPermissionRequired())) {
        result = operationContext.getOperationHandler().process(serializationService,
            operationContext.getFromRequest().apply(request), context);
      } else {
        logger.warn("Received unauthorized request");
        recordAuthorizationViolation(context);
        result = Failure.of(ProtobufResponseUtilities.makeErrorResponse(AUTHORIZATION_FAILED,
            "User isn't authorized for this operation."));
      }
    } catch (InvalidExecutionContextException exception) {
      logger.error("Invalid execution context found for operation {}", requestType);
      result = Failure.of(ProtobufResponseUtilities.makeErrorResponse(UNSUPPORTED_OPERATION,
          "Invalid execution context found for operation."));
    }

    builder = (ClientProtocol.Response.Builder) result.map(operationContext.getToResponse(),
        operationContext.getToErrorResponse());
    return builder.build();
  }

  private void recordAuthorizationViolation(MessageExecutionContext context) {
    ProtobufClientStatistics statistics = context.getStatistics();
    statistics.incAuthorizationViolations();
  }
}
