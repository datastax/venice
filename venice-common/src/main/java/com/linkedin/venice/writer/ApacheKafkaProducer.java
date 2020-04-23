package com.linkedin.venice.writer;

import com.linkedin.venice.exceptions.ConfigurationException;
import com.linkedin.venice.exceptions.VeniceException;
import com.linkedin.venice.kafka.protocol.KafkaMessageEnvelope;
import com.linkedin.venice.message.KafkaKey;
import com.linkedin.venice.serialization.KafkaKeySerializer;
import com.linkedin.venice.serialization.avro.KafkaValueSerializer;
import com.linkedin.venice.utils.KafkaSSLUtils;
import com.linkedin.venice.utils.VeniceProperties;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.metrics.Measurable;
import org.apache.log4j.Logger;


/**
 * Implementation of the Kafka Producer for sending messages to Kafka.
 */
public class ApacheKafkaProducer implements KafkaProducerWrapper {
  public static final String PROPERTIES_KAFKA_PREFIX = "kafka.";
  private static final Logger LOGGER = Logger.getLogger(ApacheKafkaProducer.class);

  /**
   * Mandatory Kafka SSL configs when SSL is enabled.
   */
  private static final List<String> KAFKA_SSL_MANDATORY_CONFIGS = Arrays.asList(
      CommonClientConfigs.SECURITY_PROTOCOL_CONFIG,
      SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG,
      SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG,
      SslConfigs.SSL_KEYSTORE_TYPE_CONFIG,
      SslConfigs.SSL_KEY_PASSWORD_CONFIG,
      SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG,
      SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG,
      SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG,
      SslConfigs.SSL_KEYMANAGER_ALGORITHM_CONFIG,
      SslConfigs.SSL_TRUSTMANAGER_ALGORITHM_CONFIG,
      SslConfigs.SSL_SECURE_RANDOM_IMPLEMENTATION_CONFIG
  );

  private final KafkaProducer<KafkaKey, KafkaMessageEnvelope> producer;

  public ApacheKafkaProducer(VeniceProperties props) {
    this(props, true);
  }

  /**
   * @param props containing producer configs
   * @param strictConfigs if true, the {@param props} will be validated to ensure no mandatory configs are badly overridden
   *                      if false, the check will not happen (useful for tests only)
   */
  protected ApacheKafkaProducer(VeniceProperties props, boolean strictConfigs) {
    /** TODO: Consider making these default settings part of {@link VeniceWriter} or {@link KafkaProducerWrapper} */
    Properties properties = getKafkaPropertiesFromVeniceProps(props);

    // TODO : For sending control message, this is not required. Move this higher in the stack.
    validateProp(properties, strictConfigs, ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KafkaKeySerializer.class.getName());
    validateProp(properties, strictConfigs, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaValueSerializer.class.getName());

    // This is to guarantee ordering, even in the face of failures.
    validateProp(properties, strictConfigs, ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1");

    // This will ensure the durability on Kafka broker side
    validateProp(properties, strictConfigs, ProducerConfig.ACKS_CONFIG, "-1");

    if (!properties.containsKey(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG)) {
      // if not specified, set default request timeout as 5 minutes (300000ms)
      properties.setProperty(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "300000");
    }
    if (!properties.containsKey(ProducerConfig.RETRIES_CONFIG)) {
      // if not specified, retry infinitely
      properties.setProperty(ProducerConfig.RETRIES_CONFIG, Integer.toString(Integer.MAX_VALUE));
    }

    // Hard-coded backoff config to be 1 sec
    validateProp(properties, strictConfigs, ProducerConfig.RETRY_BACKOFF_MS_CONFIG, "1000");
    // Block if buffer is full
    validateProp(properties, strictConfigs, ProducerConfig.MAX_BLOCK_MS_CONFIG, String.valueOf(Long.MAX_VALUE));

    if (properties.containsKey(ProducerConfig.COMPRESSION_TYPE_CONFIG)) {
      LOGGER.info("Compression type explicitly specified by config: " + properties.getProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG));
    } else {
      /**
       * In general, 'gzip' compression ratio is the best among all the available codecs:
       * 1. none
       * 2. lz4
       * 3. gzip
       * 4. snappy
       *
       * We want to minimize the cross-COLO bandwidth usage.
       */
      properties.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");
    }

    //Setup ssl config if needed.
    if (validateAndCopyKafakaSSLConfig(props, properties)) {
      LOGGER.info("Will initialize an SSL Kafka producer");
    } else {
      LOGGER.info("Will initialize a non-SSL Kafka producer");
    }

    if (!properties.containsKey(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)) {
      throw new ConfigurationException("Props key not found: " + PROPERTIES_KAFKA_PREFIX + ProducerConfig.BOOTSTRAP_SERVERS_CONFIG);
    }
    LOGGER.info("Constructing KafkaProducer with the following properties: " + properties.toString());

    producer = new KafkaProducer<>(properties);
    // TODO: Consider making the choice of partitioner implementation configurable
  }

  /**
   * Function which sets some needed defaults... Also bubbles up an exception in order
   * to fail fast if any calling class tries to override these defaults.
   *
   * TODO: Decide if this belongs here or higher up the call-stack
   */
  private void validateProp(Properties properties, boolean strictConfigs, String requiredConfigKey, String requiredConfigValue) {
    String actualConfigValue = properties.getProperty(requiredConfigKey);
    if (actualConfigValue == null) {
      properties.setProperty(requiredConfigKey, requiredConfigValue);
    } else if (!actualConfigValue.equals(requiredConfigValue) && strictConfigs) {
      // We fail fast rather than attempting to use non-standard serializers
      throw new VeniceException("The Kafka Producer must use certain configuration settings in order to work properly. " +
          "requiredConfigKey: '" + requiredConfigKey +
          "', requiredConfigValue: '" + requiredConfigValue +
          "', actualConfigValue: '" + actualConfigValue + "'.");
    }
  }

  /**
   * N.B.: This is an expensive call, the result of which should be cached.
   *
   * @param topic for which we want to request the number of partitions.
   * @return the number of partitions for this topic.
   */
  public int getNumberOfPartitions(String topic) {
    // TODO: This blocks forever. We need to be able to interrupt it and throw if it "times out".
    return producer.partitionsFor(topic).size();
  }

  /**
   * Sends a message to the Kafka Producer. If everything is set up correctly, it will show up in Kafka log.
   * @param topic - The topic to be sent to.
   * @param key - The key of the message to be sent.
   * @param value - The {@link KafkaMessageEnvelope}, which acts as the Kafka value.
   * @param callback - The callback function, which will be triggered when Kafka client sends out the message.
   * */
  @Override
  public Future<RecordMetadata> sendMessage(String topic, KafkaKey key, KafkaMessageEnvelope value, int partition, Callback callback) {
    ProducerRecord<KafkaKey, KafkaMessageEnvelope> kafkaRecord = new ProducerRecord<>(topic, partition, key, value);
    return sendMessage(kafkaRecord, callback);
  }

  @Override
  public Future<RecordMetadata> sendMessage(ProducerRecord<KafkaKey, KafkaMessageEnvelope> record, Callback callback) {
    try {
      return producer.send(record, callback);
     }catch (Exception e) {
      throw new VeniceException(
          "Got an error while trying to produce message into Kafka. Topic: '" + record.topic() + "', partition: " + record.partition(), e);
    }
  }

  @Override
  public void flush() {
    if (producer != null) {
      producer.flush();
    }
  }

  @Override
  public void close(int closeTimeOutMs) {
    if (producer != null) {
      // Flush out all the messages in the producer buffer
      producer.flush();
      LOGGER.info("Flushed all the messages in producer before closing");
      producer.close(closeTimeOutMs, TimeUnit.MILLISECONDS);
    }
  }

  @Override
  public Map<String, Double> getMeasurableProducerMetrics() {
    Map<String, Double> extractedMetrics = new HashMap<>();
    for (Map.Entry<MetricName, ? extends Metric> entry : producer.metrics().entrySet()) {
      try {
        Object value = entry.getValue().metricValue();
        if (value instanceof Double) {
          extractedMetrics.put(entry.getKey().name(), (Double) value);
        }
      } catch (Exception e) {
        LOGGER.info("Caught exception: " + e.getMessage() + " when attempting to get producer metrics. "
            + "Incomplete metrics might be returned.");
      }
    }
    return extractedMetrics;
  }

  /**
   * @return the leader for the specified {@param topic} and {@param partition}, if any
   * @throws VeniceException if there is no leader
   */
  @Override
  public String getBrokerLeaderHostname(String topic, int partition) {
    Node leader = producer.partitionsFor(topic).get(partition).leader();
    if (leader != null) {
      return leader.host() + "/" + leader.id();
    } else {
      throw new VeniceException("No broker leader for topic '" + topic + ", partition: " + partition);
    }
  }

  /**
   * This class takes in all properties that begin with "{@value #PROPERTIES_KAFKA_PREFIX}" and emits the
   * rest of the properties.
   *
   * It omits those properties that do not begin with "{@value #PROPERTIES_KAFKA_PREFIX}".
   *
   * TODO: Consider making this logic part of {@link VeniceWriter} or {@link KafkaProducerWrapper}.
  */
  private Properties getKafkaPropertiesFromVeniceProps(VeniceProperties props) {
    VeniceProperties kafkaProps = props.clipAndFilterNamespace(PROPERTIES_KAFKA_PREFIX);
    return kafkaProps.toProperties();
  }

  /**
   * This function will extract SSL related config if Kafka SSL is enabled.
   *
   * @param veniceProperties
   * @param properties
   * @return whether Kafka SSL is enabled or not
   */
  public static boolean validateAndCopyKafakaSSLConfig(VeniceProperties veniceProperties, Properties properties) {
    if (!veniceProperties.containsKey(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG)) {
      // No security protocol specified
      return false;
    }
    String kafkaProtocol = veniceProperties.getString(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG);
    if (!KafkaSSLUtils.isKafkaProtocolValid(kafkaProtocol)) {
      throw new VeniceException("Invalid Kafka protocol specified: " + kafkaProtocol);
    }
    if (!KafkaSSLUtils.isKafkaSSLProtocol(kafkaProtocol)) {
      // TLS/SSL is not enabled
      return false;
    }
    // Since SSL is enabled, the following configs are mandatory
    KAFKA_SSL_MANDATORY_CONFIGS.forEach( config -> {
      if (!veniceProperties.containsKey(config)) {
        throw new VeniceException(config + " is required when Kafka SSL is enabled");
      }
      properties.setProperty(config, veniceProperties.getString(config));
    });
    return true;
  }
}
