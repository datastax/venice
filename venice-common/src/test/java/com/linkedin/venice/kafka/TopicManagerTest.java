package com.linkedin.venice.kafka;

import com.linkedin.venice.integration.utils.KafkaBrokerWrapper;
import com.linkedin.venice.integration.utils.ServiceFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.linkedin.venice.serialization.DefaultSerializer;
import com.linkedin.venice.utils.MockTime;
import com.linkedin.venice.utils.TestUtils;
import com.linkedin.venice.utils.VeniceProperties;
import com.linkedin.venice.writer.ApacheKafkaProducer;
import com.linkedin.venice.writer.VeniceWriter;
import kafka.log.LogConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.PartitionInfo;
import org.apache.log4j.Logger;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.mockito.Mockito.*;

public class TopicManagerTest {

  private static final Logger LOGGER = Logger.getLogger(TopicManagerTest.class);

  /** Wait time for {@link #manager} operations, in seconds */
  private static final int WAIT_TIME = 10;

  private KafkaBrokerWrapper kafka;
  private TopicManager manager;
  private MockTime mockTime;

  private String getTopic() {
    String callingFunction = Thread.currentThread().getStackTrace()[2].getMethodName();
    String topicName = TestUtils.getUniqueString(callingFunction);
    int partitions = 1;
    int replicas = 1;
    manager.createTopic(topicName, partitions, replicas, false);
    TestUtils.waitForNonDeterministicAssertion(WAIT_TIME, TimeUnit.SECONDS,
        () -> Assert.assertTrue(manager.containsTopic(topicName)));
    return topicName;
  }

  @BeforeClass
  public void setup() {
    mockTime = new MockTime();
    kafka = ServiceFactory.getKafkaBroker(mockTime);
    manager = new TopicManager(kafka.getZkAddress());
  }

  @AfterClass
  public void teardown() throws IOException {
    kafka.close();
    manager.close();
  }

  @Test
  public void testCreateTopic() throws Exception {
    String topicName = getTopic();
    manager.createTopic(topicName, 1, 1, true); /* should be noop */
    Assert.assertTrue(manager.containsTopic(topicName));
  }

  @Test
  public void testDeleteTopic() throws InterruptedException {
    String topicName = getTopic();
    manager.deleteTopic(topicName);
    // (delete is async)
    TestUtils.waitForNonDeterministicAssertion(WAIT_TIME, TimeUnit.SECONDS,
        () -> Assert.assertFalse(manager.containsTopic(topicName)));
  }

  @Test
  public void testSyncDeleteTopic() throws InterruptedException {
    String topicName = getTopic();
    // Delete that topic
    manager.syncDeleteTopic(topicName);
    Assert.assertFalse(manager.containsTopic(topicName));
  }

  @Test
  public void testGetLastOffsets() {
    String topic = getTopic();
    Map<Integer, Long> lastOffsets = manager.getLatestOffsets(topic);
    TestUtils.waitForNonDeterministicAssertion(2, TimeUnit.SECONDS, () -> {
      Assert.assertTrue(lastOffsets.containsKey(0), "single partition topic has an offset for partition 0");
      Assert.assertEquals(lastOffsets.keySet().size(), 1, "single partition topic has only an offset for one partition");
      Assert.assertEquals(lastOffsets.get(0).longValue(), 0L, "new topic must end at partition 0");
    });
  }

  @Test
  public void testListOffsetsOnEmptyTopic(){
    KafkaConsumer<byte[], byte[]> mockConsumer = mock(KafkaConsumer.class);
    doReturn(new HashMap<String, List<PartitionInfo>>()).when(mockConsumer).listTopics();
    Map<Integer, Long> offsets = manager.getLatestOffsets("myTopic");
    Assert.assertEquals(offsets.size(), 0);
  }

  @Test
  public void testGetOffsetsByTime() throws InterruptedException, ExecutionException {
    final long START_TIME = 10;
    final long TIME_SKIP = 10000;
    final int NUMBER_OF_MESSAGES = 100;
    LOGGER.info("Current time at the start of testGetOffsetsByTime: " + START_TIME);

    // Setup
    mockTime.setTime(START_TIME);
    String topicName = getTopic();
    Properties properties = new Properties();
    properties.put(ApacheKafkaProducer.PROPERTIES_KAFKA_PREFIX + ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getAddress());
    VeniceWriter<byte[], byte[]> veniceWriter = new VeniceWriter<>(new VeniceProperties(properties), topicName, new DefaultSerializer(), new DefaultSerializer());

    // Test starting conditions
    assertOffsetsByTime(topicName, 0, 0);

    // Populate messages
    mockTime.addMilliseconds(TIME_SKIP);
    RecordMetadata[] offsetsByMessageNumber = new RecordMetadata[NUMBER_OF_MESSAGES];
    for (int messageNumber = 0; messageNumber < NUMBER_OF_MESSAGES; messageNumber++) {
      byte[] key = new String("key" + messageNumber).getBytes();
      byte[] value = new String("value" + messageNumber).getBytes();
      offsetsByMessageNumber[messageNumber] = veniceWriter.put(key, value, 0, null).get();
      long offset = offsetsByMessageNumber[messageNumber].offset();
      LOGGER.info("Wrote messageNumber: " + messageNumber + ", at time: " + mockTime + ", offset: " + offset);
      mockTime.addMilliseconds(1);
    }

    assertOffsetsByTime(topicName, 0, 0);
    Map<Integer, Long> latestOffsets = manager.getLatestOffsets(topicName);
    LOGGER.info("latest offsets: " + latestOffsets);
    latestOffsets.forEach((partition, offset) -> Assert.assertTrue(offset >= NUMBER_OF_MESSAGES,
        "When asking the latest offsets, partition " + partition + " has an unexpected offset."));

    // We start at 1, skipping message number 0, because its offset is annoying to guess, because of control messages.
    for (int messageNumber = 1; messageNumber < NUMBER_OF_MESSAGES; messageNumber++) {
      long messageTime = START_TIME + TIME_SKIP + messageNumber;
      long returnedOffset = offsetsByMessageNumber[messageNumber].offset();
      assertOffsetsByTime(topicName, messageTime, returnedOffset);
    }

    long futureTime = 1000*1000*1000;
    assertOffsetsByTime(topicName, futureTime, NUMBER_OF_MESSAGES + 1);
  }

  private void assertOffsetsByTime(String topicName, long time, long expectedOffset) {
    LOGGER.info("Asking for time: " + time + ", expecting offset: " + expectedOffset);
    Map<Integer, Long> offsets = manager.getOffsetsByTime(topicName, time);
    offsets.forEach((partition, offset) -> Assert.assertEquals(offset, new Long(expectedOffset),
        "When asking for timestamp " + time + ", partition " + partition + " has an unexpected offset."));

  }

  @Test
  public void testGetTopicConfig() {
    String topic = TestUtils.getUniqueString("topic");
    manager.createTopic(topic, 1, 1, true);
    Properties topicProperties = manager.getTopicConfig(topic);
    Assert.assertTrue(topicProperties.containsKey(LogConfig.RetentionMsProp()));
    Assert.assertTrue(Long.parseLong(topicProperties.getProperty(LogConfig.RetentionMsProp())) > 0,
        "retention.ms should be positive");
  }

  @Test (expectedExceptions = TopicDoesNotExistException.class)
  public void testGetTopicConfigWithUnknownTopic() {
    String topic = TestUtils.getUniqueString("topic");
    manager.getTopicConfig(topic);
  }

  @Test
  public void testUpdateTopicRetention() throws InterruptedException {
    String topic = TestUtils.getUniqueString("topic");
    manager.createTopic(topic, 1, 1, true);
    manager.updateTopicRetention(topic, 0);
    Properties topicProperties = manager.getTopicConfig(topic);
    Assert.assertEquals(topicProperties.getProperty(LogConfig.RetentionMsProp()), "0");
  }

}