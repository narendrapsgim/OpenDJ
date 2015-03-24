/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2015 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import static org.opends.messages.JebMessages.*;
import static org.opends.server.backends.pluggable.IndexOutputBuffer.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.backends.pluggable.Importer.IndexManager;

/**
 * The buffer class is used to process a buffer from the temporary index files
 * during phase 2 processing.
 */
final class IndexInputBuffer implements Comparable<IndexInputBuffer>
{

  /** Possible states while reading a record. */
  private static enum RecordState
  {
    START, NEED_INSERT_ID_SET, NEED_DELETE_ID_SET
  }

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final IndexManager indexMgr;
  private final FileChannel channel;
  private final long begin;
  private final long end;
  private final int bufferID;

  private long offset;
  private final ByteBuffer cache;

  /** Next fields are the fetched record data. */
  private Integer indexID;
  private final ByteStringBuilder keyBuffer = new ByteStringBuilder(128);
  private RecordState recordState = RecordState.START;

  /**
   * Creates a new index input buffer.
   *
   * @param indexMgr
   *          The index manager.
   * @param channel
   *          The index file channel.
   * @param begin
   *          The position of the start of the buffer in the scratch file.
   * @param end
   *          The position of the end of the buffer in the scratch file.
   * @param bufferID
   *          The buffer ID.
   * @param cacheSize
   *          The cache size.
   * @throws IOException
   *           If an IO error occurred when priming the cache.
   */
  public IndexInputBuffer(IndexManager indexMgr, FileChannel channel,
      long begin, long end, int bufferID, int cacheSize) throws IOException
  {
    this.indexMgr = indexMgr;
    this.channel = channel;
    this.begin = begin;
    this.end = end;
    this.offset = 0;
    this.bufferID = bufferID;
    this.cache = ByteBuffer.allocate(Math.max(cacheSize - 384, 256));

    loadCache();
    cache.flip();
  }

  private void loadCache() throws IOException
  {
    channel.position(begin + offset);
    long leftToRead = end - (begin + offset);
    long bytesToRead;
    if (leftToRead < cache.remaining())
    {
      cache.limit((int) (cache.position() + leftToRead));
      bytesToRead = leftToRead;
    }
    else
    {
      bytesToRead = Math.min((end - offset), cache.remaining());
    }
    int bytesRead = 0;
    while (bytesRead < bytesToRead)
    {
      bytesRead += channel.read(cache);
    }
    offset += bytesRead;
    indexMgr.addBytesRead(bytesRead);
  }

  /**
   * Returns {@code true} if this buffer has more data.
   *
   * @return {@code true} if this buffer has more data.
   * @throws IOException
   *           If an IO error occurred.
   */
  public boolean hasMoreData() throws IOException
  {
    boolean hasMore = begin + offset < end;
    return cache.remaining() != 0 || hasMore;
  }

  /**
   * Returns the length of the next key.
   *
   * @return The length of the next key.
   */
  public int getKeyLen()
  {
    return keyBuffer.length();
  }

  /**
   * Fetches the next key into the provided byte string builder.
   *
   * @param b
   *          A builder where to fetch the key
   */
  public void fetchKey(ByteStringBuilder b)
  {
    b.clear().append(keyBuffer);
  }

  /**
   * Returns the index ID of the next record.
   *
   * @return The index ID of the next record.
   */
  public Integer getIndexID()
  {
    if (indexID == null)
    {
      try
      {
        fetchNextRecord();
      }
      catch (IOException ex)
      {
        logger.error(ERR_JEB_IMPORT_BUFFER_IO_ERROR, indexMgr.getBufferFileName());
        throw new RuntimeException(ex);
      }
    }
    return indexID;
  }

  /**
   * Reads the next record from the buffer, skipping any remaining data in the
   * current record.
   *
   * @throws IOException
   *           If an IO error occurred.
   */
  public void fetchNextRecord() throws IOException
  {
    switch (recordState)
    {
    case START:
      // Nothing to skip.
      break;
    case NEED_INSERT_ID_SET:
      // The previous record's ID sets were not read, so skip them both.
      mergeIDSet(null);
      mergeIDSet(null);
      break;
    case NEED_DELETE_ID_SET:
      // The previous record's delete ID set was not read, so skip it.
      mergeIDSet(null);
      break;
    }

    indexID = getInt();

    ensureData(20);
    int keyLen = getInt();
    ensureData(keyLen);
    keyBuffer.clear().append(cache, keyLen);

    recordState = RecordState.NEED_INSERT_ID_SET;
  }

  private int getInt() throws IOException
  {
    ensureData(INT_SIZE);
    return cache.getInt();
  }

  private long getLong() throws IOException
  {
    ensureData(LONG_SIZE);
    return cache.getLong();
  }

  /**
   * Reads the next ID set from the record and merges it with the provided ID
   * set.
   *
   * @param idSet
   *          The ID set to be merged.
   * @throws IOException
   *           If an IO error occurred.
   */
  public void mergeIDSet(ImportIDSet idSet) throws IOException
  {
    if (recordState == RecordState.START)
    {
      throw new IllegalStateException();
    }

    ensureData(20);
    int keyCount = getInt();
    for (int k = 0; k < keyCount; k++)
    {
      long entryID = getLong();

      // idSet will be null if skipping.
      if (idSet != null)
      {
        idSet.addEntryID(entryID);
      }
    }

    switch (recordState)
    {
    case START:
      throw new IllegalStateException();
    case NEED_INSERT_ID_SET:
      recordState = RecordState.NEED_DELETE_ID_SET;
      break;
    case NEED_DELETE_ID_SET:
      recordState = RecordState.START;
      break;
    }
  }

  private boolean ensureData(int len) throws IOException
  {
    if (cache.remaining() == 0)
    {
      cache.clear();
      loadCache();
      cache.flip();
      return true;
    }
    else if (cache.remaining() < len)
    {
      cache.compact();
      loadCache();
      cache.flip();
      return true;
    }
    return false;
  }

  /**
   * Compares this buffer with the provided key and index ID.
   *
   * @param key
   *          The key.
   * @param indexID
   *          The index ID.
   * @return true if this buffer represent the same key and indexID, false otherwise.
   */
  boolean sameKeyAndIndexID(final ByteStringBuilder key, Integer indexID)
  {
    ensureRecordFetched();
    return Importer.indexComparator.compare(keyBuffer, key) == 0
        && this.indexID.equals(indexID);
  }

  /** {@inheritDoc} */
  @Override
  public int compareTo(IndexInputBuffer o)
  {
    // used in remove.
    if (this == o)
    {
      return 0;
    }

    ensureRecordFetched();
    o.ensureRecordFetched();

    int cmp = Importer.indexComparator.compare(keyBuffer, o.keyBuffer);
    if (cmp == 0)
    {
      cmp = indexID.intValue() - o.getIndexID().intValue();
      if (cmp == 0)
      {
        return bufferID - o.bufferID;
      }
    }
    return cmp;
  }

  private void ensureRecordFetched()
  {
    if (keyBuffer.length() == 0)
    {
      getIndexID();
    }
  }
}
