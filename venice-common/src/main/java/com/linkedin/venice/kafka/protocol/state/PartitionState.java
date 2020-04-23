/**
 * Autogenerated by Avro
 * 
 * DO NOT EDIT DIRECTLY
 */
package com.linkedin.venice.kafka.protocol.state;

@SuppressWarnings("all")
/** This record holds the state necessary for a consumer to checkpoint its progress when consuming a Venice partition. When provided the state in this record, a consumer should thus be able to resume consuming midway through a stream. */
public class PartitionState extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  public static final org.apache.avro.Schema SCHEMA$ = org.apache.avro.Schema.parse("{\"type\":\"record\",\"name\":\"PartitionState\",\"namespace\":\"com.linkedin.venice.kafka.protocol.state\",\"fields\":[{\"name\":\"offset\",\"type\":\"long\",\"doc\":\"The last Kafka offset consumed successfully in this partition from version topic.\"},{\"name\":\"endOfPush\",\"type\":\"boolean\",\"doc\":\"Whether the EndOfPush control message was consumed in this partition.\"},{\"name\":\"lastUpdate\",\"type\":\"long\",\"doc\":\"The last time this PartitionState was updated. Can be compared against the various messageTimestamp in ProducerPartitionState in order to infer lag time between producers and the consumer maintaining this PartitionState.\"},{\"name\":\"startOfBufferReplayDestinationOffset\",\"type\":[\"null\",\"long\"],\"doc\":\"This is the offset at which the StartOfBufferReplay control message was consumed in the current partition of the destination topic. This is not the same value as the source offsets contained in the StartOfBufferReplay control message itself. The source and destination offsets act together as a synchronization marker. N.B.: null means that the SOBR control message was not received yet.\",\"default\":null},{\"name\":\"databaseInfo\",\"type\":{\"type\":\"map\",\"values\":\"string\"},\"doc\":\"A map of string -> string to store database related info, which is necessary to checkpoint\",\"default\":{}},{\"name\":\"incrementalPushInfo\",\"type\":[\"null\",{\"type\":\"record\",\"name\":\"IncrementalPush\",\"fields\":[{\"name\":\"version\",\"type\":\"string\",\"doc\":\"The version of current incremental push\"},{\"name\":\"error\",\"type\":[\"null\",\"string\"],\"doc\":\"The first error founded during in`gestion\",\"default\":null}]}],\"doc\":\"metadata of ongoing incremental push in the partition\",\"default\":null},{\"name\":\"leaderTopic\",\"type\":[\"null\",\"string\"],\"doc\":\"The topic that leader is consuming from; for leader, leaderTopic can be different from the version topic; for follower, leaderTopic is the same as version topic.\",\"default\":null},{\"name\":\"leaderOffset\",\"type\":\"long\",\"doc\":\"The last Kafka offset consumed successfully in this partition from the leader topic\",\"default\":-1},{\"name\":\"leaderGUID\",\"type\":[\"null\",{\"type\":\"fixed\",\"name\":\"GUID\",\"namespace\":\"com.linkedin.venice.kafka.protocol\",\"size\":16}],\"doc\":\"This field is deprecated since GUID is no longer able to identify the split-brain issue once 'pass-through' mode is enabled in venice writer. The field is superseded by leaderHostId and will be removed in the future \",\"default\":null},{\"name\":\"leaderHostId\",\"type\":[\"null\",\"string\"],\"doc\":\"An unique identifier (such as host name) that stands for each host. It's used to identify if there is a split-brain happened while the leader(s) re-produce records\",\"default\":null},{\"name\":\"producerStates\",\"type\":{\"type\":\"map\",\"values\":{\"type\":\"record\",\"name\":\"ProducerPartitionState\",\"fields\":[{\"name\":\"segmentNumber\",\"type\":\"int\",\"doc\":\"The current segment number corresponds to the last (highest) segment number for which we have seen a StartOfSegment control message.\"},{\"name\":\"segmentStatus\",\"type\":\"int\",\"doc\":\"The status of the current segment: 0 => NOT_STARTED, 1 => IN_PROGRESS, 2 => END_OF_INTERMEDIATE_SEGMENT, 3 => END_OF_FINAL_SEGMENT.\"},{\"name\":\"messageSequenceNumber\",\"type\":\"int\",\"doc\":\"The current message sequence number, within the current segment, which we have seen for this partition/producer pair.\"},{\"name\":\"messageTimestamp\",\"type\":\"long\",\"doc\":\"The timestamp included in the last message we have seen for this partition/producer pair.\"},{\"name\":\"checksumType\",\"type\":\"int\",\"doc\":\"The current mapping is the following: 0 => None, 1 => MD5, 2 => Adler32, 3 => CRC32.\"},{\"name\":\"checksumState\",\"type\":\"bytes\",\"doc\":\"The value of the checksum computed since the last StartOfSegment ControlMessage.\"},{\"name\":\"aggregates\",\"type\":{\"type\":\"map\",\"values\":\"long\"},\"doc\":\"The aggregates that have been computed so far since the last StartOfSegment ControlMessage.\"},{\"name\":\"debugInfo\",\"type\":{\"type\":\"map\",\"values\":\"string\"},\"doc\":\"The debug info received as part of the last StartOfSegment ControlMessage.\"}]}},\"doc\":\"A map of producer GUID -> producer state.\"}]}");
  /** The last Kafka offset consumed successfully in this partition from version topic. */
  public long offset;
  /** Whether the EndOfPush control message was consumed in this partition. */
  public boolean endOfPush;
  /** The last time this PartitionState was updated. Can be compared against the various messageTimestamp in ProducerPartitionState in order to infer lag time between producers and the consumer maintaining this PartitionState. */
  public long lastUpdate;
  /** This is the offset at which the StartOfBufferReplay control message was consumed in the current partition of the destination topic. This is not the same value as the source offsets contained in the StartOfBufferReplay control message itself. The source and destination offsets act together as a synchronization marker. N.B.: null means that the SOBR control message was not received yet. */
  public java.lang.Long startOfBufferReplayDestinationOffset;
  /** A map of string -> string to store database related info, which is necessary to checkpoint */
  public java.util.Map<java.lang.CharSequence,java.lang.CharSequence> databaseInfo;
  /** metadata of ongoing incremental push in the partition */
  public com.linkedin.venice.kafka.protocol.state.IncrementalPush incrementalPushInfo;
  /** The topic that leader is consuming from; for leader, leaderTopic can be different from the version topic; for follower, leaderTopic is the same as version topic. */
  public java.lang.CharSequence leaderTopic;
  /** The last Kafka offset consumed successfully in this partition from the leader topic */
  public long leaderOffset;
  /** This field is deprecated since GUID is no longer able to identify the split-brain issue once 'pass-through' mode is enabled in venice writer. The field is superseded by leaderHostId and will be removed in the future  */
  public com.linkedin.venice.kafka.protocol.GUID leaderGUID;
  /** An unique identifier (such as host name) that stands for each host. It's used to identify if there is a split-brain happened while the leader(s) re-produce records */
  public java.lang.CharSequence leaderHostId;
  /** A map of producer GUID -> producer state. */
  public java.util.Map<java.lang.CharSequence,com.linkedin.venice.kafka.protocol.state.ProducerPartitionState> producerStates;
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call. 
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return offset;
    case 1: return endOfPush;
    case 2: return lastUpdate;
    case 3: return startOfBufferReplayDestinationOffset;
    case 4: return databaseInfo;
    case 5: return incrementalPushInfo;
    case 6: return leaderTopic;
    case 7: return leaderOffset;
    case 8: return leaderGUID;
    case 9: return leaderHostId;
    case 10: return producerStates;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }
  // Used by DatumReader.  Applications should not call. 
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: offset = (java.lang.Long)value$; break;
    case 1: endOfPush = (java.lang.Boolean)value$; break;
    case 2: lastUpdate = (java.lang.Long)value$; break;
    case 3: startOfBufferReplayDestinationOffset = (java.lang.Long)value$; break;
    case 4: databaseInfo = (java.util.Map<java.lang.CharSequence,java.lang.CharSequence>)value$; break;
    case 5: incrementalPushInfo = (com.linkedin.venice.kafka.protocol.state.IncrementalPush)value$; break;
    case 6: leaderTopic = (java.lang.CharSequence)value$; break;
    case 7: leaderOffset = (java.lang.Long)value$; break;
    case 8: leaderGUID = (com.linkedin.venice.kafka.protocol.GUID)value$; break;
    case 9: leaderHostId = (java.lang.CharSequence)value$; break;
    case 10: producerStates = (java.util.Map<java.lang.CharSequence,com.linkedin.venice.kafka.protocol.state.ProducerPartitionState>)value$; break;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }
}
