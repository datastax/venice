package com.linkedin.davinci.ingestion;

import com.linkedin.davinci.config.VeniceConfigLoader;
import com.linkedin.venice.ConfigKeys;
import com.linkedin.venice.exceptions.VeniceException;
import com.linkedin.venice.ingestion.protocol.IngestionMetricsReport;
import com.linkedin.venice.ingestion.protocol.IngestionStorageMetadata;
import com.linkedin.venice.ingestion.protocol.IngestionTaskCommand;
import com.linkedin.venice.ingestion.protocol.IngestionTaskReport;
import com.linkedin.venice.ingestion.protocol.InitializationConfigs;
import com.linkedin.venice.ingestion.protocol.ProcessShutdownCommand;
import com.linkedin.venice.ingestion.protocol.enums.IngestionAction;
import com.linkedin.venice.ingestion.protocol.enums.IngestionCommandType;
import com.linkedin.venice.ingestion.protocol.enums.IngestionComponentType;
import com.linkedin.venice.utils.ForkedJavaProcess;
import com.linkedin.venice.utils.Utils;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.log4j.Logger;

import static com.linkedin.davinci.ingestion.IngestionUtils.*;
import static com.linkedin.venice.ConfigKeys.*;


/**
 * IngestionRequestClient sends requests to target listener service and retrieves responses.
 */
public class IngestionRequestClient implements AutoCloseable, Closeable {
  private static final Logger logger = Logger.getLogger(IngestionRequestClient.class);

  private final IngestionRequestTransport ingestionRequestTransport;

  public IngestionRequestClient(int port) {
    ingestionRequestTransport = new IngestionRequestTransport(port);
  }

  public synchronized Process startForkedIngestionProcess(VeniceConfigLoader configLoader) {
    int ingestionServicePort = configLoader.getVeniceServerConfig().getIngestionServicePort();
    try {
      // Add blocking call to release target port binding.
      IngestionUtils.releaseTargetPortBinding(ingestionServicePort);
      List<String> jvmArgs = new ArrayList<>();
      for (String jvmArg : configLoader.getCombinedProperties().getString(ConfigKeys.SERVER_FORKED_PROCESS_JVM_ARGUMENT_LIST, "").split(",")) {
        if (jvmArg.length() != 0) {
          jvmArgs.add(jvmArg);
        }
      }
      // Start forking child ingestion process.
      long heartbeatTimeoutMs = configLoader.getCombinedProperties().getLong(SERVER_INGESTION_ISOLATION_HEARTBEAT_TIMEOUT_MS,
          TimeUnit.SECONDS.toMillis(60));
      Process isolatedIngestionService = ForkedJavaProcess.exec(
          IngestionService.class,
          Arrays.asList(String.valueOf(ingestionServicePort), String.valueOf(heartbeatTimeoutMs)),
          jvmArgs,
          Optional.empty()
      );
      // Wait for server in forked child process to bind the listening port.
      IngestionUtils.waitPortBinding(ingestionServicePort, 100);
      // Wait for server in forked child process to pass health check.
      waitHealthCheck(100);
      InitializationConfigs initializationConfigs = new InitializationConfigs();
      initializationConfigs.aggregatedConfigs = new HashMap<>();
      configLoader.getCombinedProperties().toProperties().forEach((key, value) -> initializationConfigs.aggregatedConfigs.put(key.toString(), value.toString()));
      // Block restoring on disk store data as it has been restored on the main process when live update suppression is enabled.
      if (configLoader.getVeniceServerConfig().freezeIngestionIfReadyToServeOrLocalDataExists()) {
        initializationConfigs.aggregatedConfigs.put(ConfigKeys.SERVER_RESTORE_DATA_PARTITIONS_ENABLED, "false");
        initializationConfigs.aggregatedConfigs.put(ConfigKeys.SERVER_RESTORE_METADATA_PARTITION_ENABLED, "false");
      }
      logger.info("Sending initialization aggregatedConfigs to child process: " + initializationConfigs.aggregatedConfigs);
      ingestionRequestTransport.sendRequest(IngestionAction.INIT, initializationConfigs);
      logger.info("Isolated ingestion service initialization finished.");
      return isolatedIngestionService;
    } catch (Exception e) {
      throw new VeniceException("Exception caught during initialization of ingestion service:", e);
    }
  }

  public void startConsumption(String topicName, int partitionId) {
    // Send ingestion request to ingestion service.
    IngestionTaskCommand ingestionTaskCommand = new IngestionTaskCommand();
    ingestionTaskCommand.commandType = IngestionCommandType.START_CONSUMPTION.getValue();
    ingestionTaskCommand.topicName = topicName;
    ingestionTaskCommand.partitionId = partitionId;
    logger.info("Sending START_CONSUMPTION request to child process: "  + ingestionTaskCommand);
    try {
      ingestionRequestTransport.sendRequest(IngestionAction.COMMAND, ingestionTaskCommand);
    } catch (Exception e) {
      throw new VeniceException("Exception caught during startConsumption of topic: " + topicName + ", partition: " + partitionId, e);
    }
  }

  public void stopConsumption(String topicName, int partitionId) {
    IngestionTaskCommand ingestionTaskCommand = new IngestionTaskCommand();
    ingestionTaskCommand.commandType = IngestionCommandType.STOP_CONSUMPTION.getValue();
    ingestionTaskCommand.topicName = topicName;
    ingestionTaskCommand.partitionId = partitionId;
    logger.info("Sending STOP_CONSUMPTION request to child process: "  + ingestionTaskCommand);
    try {
      ingestionRequestTransport.sendRequest(IngestionAction.COMMAND, ingestionTaskCommand);
    } catch (Exception e) {
      throw new VeniceException("Exception caught during stopConsumption of topic: " + topicName + ", partition: " + partitionId, e);
    }
  }

  public void killConsumptionTask(String topicName) {
    IngestionTaskCommand ingestionTaskCommand = new IngestionTaskCommand();
    ingestionTaskCommand.commandType = IngestionCommandType.KILL_CONSUMPTION.getValue();
    ingestionTaskCommand.topicName = topicName;
    logger.info("Sending KILL_CONSUMPTION request to child process: "  + ingestionTaskCommand);
    try {
      ingestionRequestTransport.sendRequest(IngestionAction.COMMAND, ingestionTaskCommand);
    } catch (Exception e) {
      throw new VeniceException("Exception caught during killConsumptionTask of topic: " + topicName, e);
    }
  }

  public void removeStorageEngine(String topicName) {
    IngestionTaskCommand ingestionTaskCommand = new IngestionTaskCommand();
    ingestionTaskCommand.commandType = IngestionCommandType.REMOVE_STORAGE_ENGINE.getValue();
    ingestionTaskCommand.topicName = topicName;
    logger.info("Sending REMOVE_STORAGE_ENGINE request to child process: "  + ingestionTaskCommand);
    try {
      ingestionRequestTransport.sendRequest(IngestionAction.COMMAND, ingestionTaskCommand);
    } catch (Exception e) {
      throw new VeniceException("Encounter exception during closeTopicStorage of topic: " + topicName, e);
    }
  }

  public void unsubscribeTopicPartition(String topicName, int partitionId) {
    IngestionTaskCommand ingestionTaskCommand = new IngestionTaskCommand();
    ingestionTaskCommand.commandType = IngestionCommandType.REMOVE_PARTITION.getValue();
    ingestionTaskCommand.topicName = topicName;
    ingestionTaskCommand.partitionId = partitionId;
    logger.info("Sending REMOVE_PARTITION request to child process: "  + ingestionTaskCommand);
    try {
      ingestionRequestTransport.sendRequest(IngestionAction.COMMAND, ingestionTaskCommand);
    } catch (Exception e) {
      throw new VeniceException("Encounter exception during closeTopicStorage of topic: " + topicName, e);
    }
  }

  public boolean updateMetadata(IngestionStorageMetadata ingestionStorageMetadata) {
    try {
      logger.info("Sending UPDATE_METADATA request to child process: "  + ingestionStorageMetadata);
      ingestionRequestTransport.sendRequest(IngestionAction.UPDATE_METADATA, ingestionStorageMetadata);
      return true;
    } catch (Exception e) {
      /**
       * We only log the exception when failing to persist metadata updates into child process.
       * Child process might crashed, but it will be respawned and will be able to receive future updates.
       */
      logger.warn("Encounter exception when sending metadata updates to child process for topic: "
          + ingestionStorageMetadata.topicName + ", partition: " + ingestionStorageMetadata.partitionId, e);
      return false;
    }
  }

  public void shutdownForkedProcessComponent(IngestionComponentType ingestionComponentType) {
    // Send ingestion request to ingestion service.
    ProcessShutdownCommand processShutdownCommand = new ProcessShutdownCommand();
    processShutdownCommand.componentType = ingestionComponentType.getValue();
    logger.info("Sending SHUTDOWN_COMPONENT request to child process: "  + processShutdownCommand);
    try {
      ingestionRequestTransport.sendRequest(IngestionAction.SHUTDOWN_COMPONENT, processShutdownCommand);
    } catch (Exception e) {
      throw new VeniceException("Received exception in component shutdown", e);
    }
  }

  public boolean reportIngestionTask(IngestionTaskReport report) {
    String topicName = report.topicName.toString();
    int partitionId = report.partitionId;
    logger.info("Sending ingestion report: " + report);
    try {
      ingestionRequestTransport.sendRequest(IngestionAction.REPORT, report);
      return true;
    } catch (Exception e) {
      logger.warn("Failed to send report with exception for topic: " + topicName + ", partition: " + partitionId , e);
      return false;
    }
  }

  public boolean collectMetrics(IngestionProcessStats ingestionProcessStats) {
    try {
      IngestionMetricsReport metricsReport = ingestionRequestTransport.sendRequest(IngestionAction.METRIC, getDummyCommand());
      if (logger.isDebugEnabled()) {
        logger.debug("Collected " + metricsReport.aggregatedMetrics.size() + " metrics from isolated ingestion service.");
      }
      ingestionProcessStats.updateMetricMap(metricsReport.aggregatedMetrics);
      return true;
    } catch (Exception e) {
      logger.warn("Unable to collect metrics from ingestion service", e);
      return false;
    }
  }

  public boolean sendHeartbeatRequest() {
    try {
      ingestionRequestTransport.sendRequest(IngestionAction.HEARTBEAT, getDummyCommand());
      return true;
    } catch (Exception e) {
      logger.warn("Unable to get heartbeat from ingestion service", e);
      return false;
    }
  }

  @Override
  public void close() {
    ingestionRequestTransport.close();
  }

  private void waitHealthCheck(int maxAttempt) throws Exception {
    long waitTime = 100;
    int retryCount = 0;
    while (true) {
      try {
        if (sendHeartbeatRequest()) {
          logger.info("Ingestion service server health check passed.");
          break;
        } else {
          throw new VeniceException("Got non-OK response from ingestion service.");
        }
      } catch (Exception e) {
        retryCount++;
        if (retryCount > maxAttempt) {
          logger.info("Fail to pass health-check for ingestion service after " + maxAttempt + " retries.");
          throw e;
        }
        Utils.sleep(waitTime);
      }
    }
  }
}
