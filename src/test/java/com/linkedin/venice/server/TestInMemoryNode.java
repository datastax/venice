package com.linkedin.venice.server;

import com.linkedin.venice.config.GlobalConfiguration;
import com.linkedin.venice.storage.InMemoryStorageNode;

import com.linkedin.venice.storage.InMemoryStoragePartition;
import junit.framework.Assert;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests the operations of the InMemoryStorageNode class
 * 1. Constructor assigns parameters successfully
 * 2. Put, Get, Delete and Overwriting Put on Node
 * 3. null is returned on non-existent key
 * 4. Partition addition and removal
 * 5. Partition failure operations (re-adding, removing twice)
 * 6. Operating on a non-existent partition returns null
 */
public class TestInMemoryNode {

  @BeforeClass
  public static void initConfig() {
    GlobalConfiguration.initialize("");         // config file for testing
  }

  @Test
  public void testNodeConstructor() {

    InMemoryStorageNode testNode = new InMemoryStorageNode(1);
    Assert.assertEquals(1, testNode.getNodeId());

    testNode = new InMemoryStorageNode(5);
    Assert.assertEquals(5, testNode.getNodeId());

  }

  @Test
  public void testNodeOperations() {

    InMemoryStorageNode testNode = new InMemoryStorageNode(1);
    testNode.addPartition(1);

    // test basic put and get
    testNode.put(1, "key", "value");
    testNode.put(1, "key2", "value2");
    Assert.assertEquals("value", testNode.get(1, "key"));
    Assert.assertEquals("value2", testNode.get(1, "key2"));

    // test overwrite
    testNode.put(1, "key2", "value3");
    Assert.assertEquals("value3", testNode.get(1, "key2"));

    // test delete
    testNode.delete(1, "key");
    Assert.assertNull(testNode.get(1, "key"));
    Assert.assertEquals("value3", testNode.get(1, "key2"));

    // test null key
    Assert.assertNull(testNode.get(1, "null_key"));

  }

  @Test
  public void testNodePartitioning() {

    InMemoryStorageNode testNode = new InMemoryStorageNode(1);    // nodeId = 1

    Assert.assertFalse(testNode.containsPartition(10));
    testNode.put(10, "dummy_key", "dummy_value");

    Assert.assertFalse(testNode.containsPartition(10));
    testNode.addPartition(10);

    Assert.assertTrue(testNode.containsPartition(10));

    InMemoryStoragePartition partition = testNode.removePartition(10);
    Assert.assertFalse(testNode.containsPartition(10));
    Assert.assertEquals(partition.getId(), 10);

  }

  @Test
  public void testPartitionFails() {

    InMemoryStorageNode testNode = new InMemoryStorageNode(1);    // nodeId = 1
    Assert.assertTrue(testNode.addPartition(1));

    // attempting to re-add partition, should return null
    Assert.assertFalse(testNode.addPartition(1));

    // should return the removed partition
    Assert.assertNotNull(testNode.removePartition(1));

    // attempting to remove non-existant partition, should return null
    Assert.assertNull(testNode.removePartition(1));


  }

  @Test
  public void testNodeNull() {

    InMemoryStorageNode testNode = new InMemoryStorageNode(1);    // nodeId = 1
    Assert.assertFalse(testNode.put(1, "dummy_key", "dummy_value")); // should not work, due to lack of available partition
    Assert.assertNull(testNode.get(1, "dummy_key"));
    Assert.assertFalse(testNode.delete(1, "dummy_key"));

  }

}
