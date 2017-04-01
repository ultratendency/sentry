/**
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
package org.apache.sentry.hdfs;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.codahale.metrics.Timer;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.HiveMetaStore.HMSHandler;
import org.apache.hadoop.hive.metastore.MetaStorePreEventListener;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.sentry.hdfs.ServiceConstants.ServerConfig;
import org.apache.sentry.provider.db.SentryMetastoreListenerPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

/**
 * Plugin implementation of {@link SentryMetastoreListenerPlugin} that hooks
 * into the sites in the {@link MetaStorePreEventListener} that deal with
 * creation/updation and deletion for paths.
 */
public class MetastorePlugin extends SentryMetastoreListenerPlugin {

  private static final Logger LOGGER = LoggerFactory.getLogger(MetastorePlugin.class);

  private static final String initializationFailureMsg = "Cache failed to initialize, cannot send path updates to Sentry." +
          " Please review HMS error logs during startup for additional information. If the initialization failure is due" +
          " to SentryMalformedPathException, you will need to rectify the malformed path in HMS db and restart HMS";

  class SyncTask implements Runnable {
    @Override
    public void run() {
      if (!notificiationLock.tryLock()) {
        // No need to sync.. as metastore is in the process of pushing an update..
        return;
      }
      if (MetastorePlugin.this.authzPaths == null) {
        LOGGER.warn(initializationFailureMsg);
        return;
      }
      try {
        long lastSeenBySentry =
            MetastorePlugin.this.getClient().getLastSeenHMSPathSeqNum();
        long lastSent = lastSentSeqNum;
        if (lastSeenBySentry != lastSent) {
          LOGGER.warn("#### Sentry not in sync with HMS [" + lastSeenBySentry + ", "
              + lastSent + "]");
          PathsUpdate fullImageUpdate =
              MetastorePlugin.this.authzPaths.createFullImageUpdate(lastSent);
          notifySentryNoLock(fullImageUpdate);
          LOGGER.warn("#### Synced Sentry with update [" + lastSent + "]");
        }
      } catch (Exception e) {
        sentryClient = null;
        LOGGER.error("Error talking to Sentry HDFS Service !!", e);
      } finally {
        syncSent = true;
        notificiationLock.unlock();
      }
    }
  }

  private final Configuration conf;
  private SentryHDFSServiceClient sentryClient;
  private volatile UpdateableAuthzPaths authzPaths;
  private Lock notificiationLock;

  // Initialized to some value > 1.
  protected static final AtomicLong seqNum = new AtomicLong(5);

  // Has to match the value of seqNum
  protected static volatile long lastSentSeqNum = seqNum.get();
  private volatile boolean syncSent = false;
  private volatile boolean initComplete = false;
  private volatile boolean queueFlushComplete = false;
  private volatile Throwable initError = null;
  private final Queue<PathsUpdate> updateQueue = new LinkedList<PathsUpdate>();

  private final ExecutorService threadPool; //NOPMD
  private final Configuration sentryConf;

  static class ProxyHMSHandler extends HMSHandler {
    public ProxyHMSHandler(String name, HiveConf conf) throws MetaException {
      super(name, conf);
    }
  }

  public MetastorePlugin(Configuration conf, Configuration sentryConf) {
    this.notificiationLock = new ReentrantLock();

    if (!(conf instanceof HiveConf)) {
        String error = "Configuration is not an instanceof HiveConf";
        LOGGER.error(error);
        throw new RuntimeException(error);
    }
    this.conf = new HiveConf((HiveConf)conf);

    this.sentryConf = new Configuration(sentryConf);
    this.conf.unset(HiveConf.ConfVars.METASTORE_PRE_EVENT_LISTENERS.varname);
    this.conf.unset(HiveConf.ConfVars.METASTORE_EVENT_LISTENERS.varname);
    this.conf.unset(HiveConf.ConfVars.METASTORE_END_FUNCTION_LISTENERS.varname);
    this.conf.unset(HiveConf.ConfVars.METASTOREURIS.varname);

    try {
      sentryClient = SentryHDFSServiceClientFactory.create(sentryConf);
    } catch (Exception e) {
      sentryClient = null;
      LOGGER.error("Could not connect to Sentry HDFS Service !!", e);
    }
    ScheduledExecutorService newThreadPool = Executors.newScheduledThreadPool(1);
    newThreadPool.scheduleWithFixedDelay(new SyncTask(),
            this.conf.getLong(ServerConfig
                            .SENTRY_HDFS_INIT_UPDATE_RETRY_DELAY_MS,
                    ServerConfig.SENTRY_HDFS_INIT_UPDATE_RETRY_DELAY_DEFAULT),
            this.conf.getLong(ServerConfig.SENTRY_HDFS_SYNC_CHECKER_PERIOD_MS,
                    ServerConfig.SENTRY_HDFS_SYNC_CHECKER_PERIOD_DEFAULT),
            TimeUnit.MILLISECONDS);
    this.threadPool = newThreadPool;
  }

  @Override
  public void addPath(String authzObj, String path) {
    List<String> pathTree = null;
    try {
      pathTree = PathsUpdate.parsePath(path);
    } catch (SentryMalformedPathException e) {
      LOGGER.error("Unexpected path in addPath: authzObj = " + authzObj + " , path = " + path);
      e.printStackTrace();
      return;
    }
    if(pathTree == null) {
      return;
    }
    LOGGER.debug("#### HMS Path Update ["
        + "OP : addPath, "
        + "authzObj : " + authzObj.toLowerCase() + ", "
        + "path : " + path + "]");
    PathsUpdate update = createHMSUpdate();
    update.newPathChange(authzObj.toLowerCase()).addToAddPaths(pathTree);
    notifySentryAndApplyLocal(update);
  }

  @Override
  public void removeAllPaths(String authzObj, List<String> childObjects) {
    LOGGER.debug("#### HMS Path Update ["
        + "OP : removeAllPaths, "
        + "authzObj : " + authzObj.toLowerCase() + ", "
        + "childObjs : " + (childObjects == null ? "[]" : childObjects) + "]");
    PathsUpdate update = createHMSUpdate();
    if (childObjects != null) {
      for (String childObj : childObjects) {
        update.newPathChange(authzObj.toLowerCase() + "." + childObj).addToDelPaths(
            Lists.newArrayList(PathsUpdate.ALL_PATHS));
      }
    }
    update.newPathChange(authzObj.toLowerCase()).addToDelPaths(
            Lists.newArrayList(PathsUpdate.ALL_PATHS));
    notifySentryAndApplyLocal(update);
  }

  @Override
  public void removePath(String authzObj, String path) {
    if ("*".equals(path)) {
      removeAllPaths(authzObj.toLowerCase(), null);
    } else {
      List<String> pathTree = null;
      try {
        pathTree = PathsUpdate.parsePath(path);
      } catch (SentryMalformedPathException e) {
        LOGGER.error("Unexpected path in removePath: authzObj = " + authzObj + " , path = " + path);
        e.printStackTrace();
        return;
      }
      if(pathTree == null) {
        return;
      }
      LOGGER.debug("#### HMS Path Update ["
          + "OP : removePath, "
          + "authzObj : " + authzObj.toLowerCase() + ", "
          + "path : " + path + "]");
      PathsUpdate update = createHMSUpdate();
      update.newPathChange(authzObj.toLowerCase()).addToDelPaths(pathTree);
      notifySentryAndApplyLocal(update);
    }
  }

  @Override
  public void renameAuthzObject(String oldName, String oldPath, String newName,
      String newPath) {
    String oldNameLC = oldName != null ? oldName.toLowerCase() : null;
    String newNameLC = newName != null ? newName.toLowerCase() : null;
    PathsUpdate update = createHMSUpdate();
    LOGGER.debug("#### HMS Path Update ["
        + "OP : renameAuthzObject, "
        + "oldName : " + oldNameLC + ","
        + "oldPath : " + oldPath + ","
        + "newName : " + newNameLC + ","
        + "newPath : " + newPath + "]");
    List<String> newPathTree = null;
    try {
      newPathTree = PathsUpdate.parsePath(newPath);
    } catch (SentryMalformedPathException e) {
      LOGGER.error("Unexpected path in renameAuthzObject while parsing newPath: oldName=" + oldName + ", oldPath=" + oldPath +
      ", newName=" + newName + ", newPath=" + newPath);
      e.printStackTrace();
      return;
    }

    if( newPathTree != null ) {
      update.newPathChange(newNameLC).addToAddPaths(newPathTree);
    }
    List<String> oldPathTree = null;
    try {
      oldPathTree = PathsUpdate.parsePath(oldPath);
    } catch (SentryMalformedPathException e) {
      LOGGER.error("Unexpected path in renameAuthzObject while parsing oldPath: oldName=" + oldName + ", oldPath=" + oldPath +
              ", newName=" + newName + ", newPath=" + newPath);
      e.printStackTrace();
      return;
    }

    if( oldPathTree != null ) {
      update.newPathChange(oldNameLC).addToDelPaths(oldPathTree);
    }
    notifySentryAndApplyLocal(update);
  }

  private SentryHDFSServiceClient getClient() {
    if (sentryClient == null) {
      try {
        sentryClient = SentryHDFSServiceClientFactory.create(sentryConf);
      } catch (Exception e) {
        sentryClient = null;
        LOGGER.error("#### Could not connect to Sentry HDFS Service !!", e);
      }
    }
    return sentryClient;
  }

  private PathsUpdate createHMSUpdate() {
    PathsUpdate update = new PathsUpdate(seqNum.incrementAndGet(), false);
    LOGGER.debug("#### Creating HMS Path Update SeqNum : [" + seqNum.get() + "]");
    return update;
  }

  protected void notifySentryNoLock(PathsUpdate update) {
    final Timer.Context timerContext =
        SentryHdfsMetricsUtil.getNotifyHMSUpdateTimer.time();
    try {
      getClient().notifyHMSUpdate(update);
    } catch (Exception e) {
      LOGGER.error("Could not send update to Sentry HDFS Service !!", e);
      SentryHdfsMetricsUtil.getFailedNotifyHMSUpdateCounter.inc();
    } finally {
      timerContext.stop();
    }
  }

  protected void notifySentry(PathsUpdate update) {
    notificiationLock.lock();
    try {
      if (!syncSent) {
        new SyncTask().run();
      }

      notifySentryNoLock(update);
    } finally {
      lastSentSeqNum = update.getSeqNum();
      notificiationLock.unlock();
      LOGGER.debug("#### HMS Path Last update sent : ["+ lastSentSeqNum + "]");
    }
  }

  protected void applyLocal(PathsUpdate update) {
    final Timer.Context timerContext =
        SentryHdfsMetricsUtil.getApplyLocalUpdateTimer.time();
    if(authzPaths == null) {
      LOGGER.error(initializationFailureMsg);
      return;
    }
    authzPaths.updatePartial(Lists.newArrayList(update), new ReentrantReadWriteLock());
    timerContext.stop();
    SentryHdfsMetricsUtil.getApplyLocalUpdateHistogram.update(
        update.getPathChanges().size());
  }

  private void notifySentryAndApplyLocal(PathsUpdate update) {
    if(authzPaths == null) {
      LOGGER.error(initializationFailureMsg);
      return;
    }
    if (initComplete) {
      processUpdate(update);
    } else {
      if (initError == null) {
        synchronized (updateQueue) {
          if (!queueFlushComplete) {
            updateQueue.add(update);
          } else {
            processUpdate(update);
          }
        }
      } else {
        StringWriter sw = new StringWriter();
        initError.printStackTrace(new PrintWriter(sw));
        LOGGER.error("#### Error initializing Metastore Plugin" +
                "[" + sw.toString() + "] !!");
        throw new RuntimeException(initError);
      }
      LOGGER.warn("#### Path update [" + update.getSeqNum() + "] not sent to Sentry.." +
              "Metastore hasn't been initialized yet !!");
    }
  }

  protected void processUpdate(PathsUpdate update) {
    applyLocal(update);
    notifySentry(update);
  }

}
