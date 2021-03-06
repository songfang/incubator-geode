/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gemstone.gemfire.management.internal.cli.commands;

import static com.gemstone.gemfire.distributed.ConfigurationProperties.*;

import com.gemstone.gemfire.cache.*;
import com.gemstone.gemfire.distributed.internal.DistributionConfig;
import com.gemstone.gemfire.internal.cache.GemFireCacheImpl;
import com.gemstone.gemfire.internal.lang.ThreadUtils;
import com.gemstone.gemfire.management.cli.Result;
import com.gemstone.gemfire.management.cli.Result.Status;
import com.gemstone.gemfire.management.internal.cli.HeadlessGfsh;
import com.gemstone.gemfire.management.internal.cli.i18n.CliStrings;
import com.gemstone.gemfire.management.internal.cli.result.*;
import com.gemstone.gemfire.management.internal.cli.result.CompositeResultData.SectionResultData;
import com.gemstone.gemfire.test.dunit.*;
import com.gemstone.gemfire.test.junit.categories.DistributedTest;
import com.gemstone.gemfire.test.junit.categories.FlakyTest;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static com.gemstone.gemfire.test.dunit.Assert.*;
import static com.gemstone.gemfire.test.dunit.IgnoredException.addIgnoredException;
import static com.gemstone.gemfire.test.dunit.Invoke.invokeInEveryVM;
import static com.gemstone.gemfire.test.dunit.LogWriterUtils.getLogWriter;
import static com.gemstone.gemfire.test.dunit.Wait.waitForCriterion;

/**
 * Dunit class for testing gemfire function commands : GC, Shutdown
 */
@Category(DistributedTest.class)
public class MiscellaneousCommandsDUnitTest extends CliCommandTestBase {

  private static final long serialVersionUID = 1L;
  private static String cachedLogLevel;

  @Override
  protected final void preTearDownCliCommandTestBase() throws Exception {
    invokeInEveryVM(new SerializableRunnable("reset log level") {
      public void run() {
        if (cachedLogLevel != null) {
          System.setProperty(DistributionConfig.GEMFIRE_PREFIX + LOG_LEVEL, cachedLogLevel);
          cachedLogLevel = null;
        }
      }
    });
  }

  @Category(FlakyTest.class) // GEODE-1034: random ports, GC sensitive, memory sensitive, HeadlessGFSH
  @Test
  public void testGCForGroup() {
    Properties localProps = new Properties();
    localProps.setProperty(NAME, "Manager");
    localProps.setProperty(GROUPS, "Group1");
    setUpJmxManagerOnVm0ThenConnect(localProps);
    String command = "gc --group=Group1";
    CommandResult cmdResult = executeCommand(command);
    cmdResult.resetToFirstLine();
    if (cmdResult != null) {
      String cmdResultStr = commandResultToString(cmdResult);
      getLogWriter().info("testGCForGroup cmdResultStr=" + cmdResultStr + "; cmdResult=" + cmdResult);
      assertEquals(Result.Status.OK, cmdResult.getStatus());
      if (cmdResult.getType().equals(ResultData.TYPE_TABULAR)) {
        TabularResultData table = (TabularResultData) cmdResult.getResultData();
        List<String> memberNames = table.retrieveAllValues(CliStrings.GC__MSG__MEMBER_NAME);
        assertEquals(true, memberNames.size() == 1 ? true : false);
      } else {
        fail("testGCForGroup failed as CommandResult should be table type");
      }
    } else {
      fail("testGCForGroup failed as did not get CommandResult");
    }
  }

  public String getMemberId() {
    Cache cache = getCache();
    return cache.getDistributedSystem().getDistributedMember().getId();
  }

  @Test
  public void testGCForMemberID() {
    setUpJmxManagerOnVm0ThenConnect(null);
    final VM vm1 = Host.getHost(0).getVM(1);
    final String vm1MemberId = (String) vm1.invoke(() -> getMemberId());
    String command = "gc --member=" + vm1MemberId;
    CommandResult cmdResult = executeCommand(command);
    cmdResult.resetToFirstLine();
    if (cmdResult != null) {
      String cmdResultStr = commandResultToString(cmdResult);
      getLogWriter().info("testGCForMemberID cmdResultStr=" + cmdResultStr);
      assertEquals(Result.Status.OK, cmdResult.getStatus());
      if (cmdResult.getType().equals(ResultData.TYPE_TABULAR)) {
        TabularResultData table = (TabularResultData) cmdResult.getResultData();
        List<String> memberNames = table.retrieveAllValues(CliStrings.GC__MSG__MEMBER_NAME);
        assertEquals(true, memberNames.size() == 1 ? true : false);
      } else {
        fail("testGCForGroup failed as CommandResult should be table type");
      }
    } else {
      fail("testGCForCluster failed as did not get CommandResult");
    }
  }

  @Test
  public void testShowLogDefault() throws IOException {
    Properties props = new Properties();
    try {
      props.setProperty(LOG_FILE, "testShowLogDefault.log");
      setUpJmxManagerOnVm0ThenConnect(props);
      final VM vm1 = Host.getHost(0).getVM(0);
      final String vm1MemberId = (String) vm1.invoke(() -> getMemberId());
      String command = "show log --member=" + vm1MemberId;
      CommandResult cmdResult = executeCommand(command);
      if (cmdResult != null) {
        String log = commandResultToString(cmdResult);
        assertNotNull(log);
        getLogWriter().info("Show Log is" + log);
        assertEquals(Result.Status.OK, cmdResult.getStatus());
      } else {
        fail("testShowLog failed as did not get CommandResult");
      }
    } finally {
      disconnectAllFromDS();
    }
  }

  @Test
  public void testShowLogNumLines() {
    Properties props = new Properties();
    props.setProperty(LOG_FILE, "testShowLogNumLines.log");
    try {
      setUpJmxManagerOnVm0ThenConnect(props);
      final VM vm1 = Host.getHost(0).getVM(0);
      final String vm1MemberId = (String) vm1.invoke(() -> getMemberId());
      String command = "show log --member=" + vm1MemberId + " --lines=50";
      CommandResult cmdResult = executeCommand(command);
      if (cmdResult != null) {
        String log = commandResultToString(cmdResult);
        assertNotNull(log);
        getLogWriter().info("Show Log is" + log);
        assertEquals(Result.Status.OK, cmdResult.getStatus());
      } else {
        fail("testShowLog failed as did not get CommandResult");
      }
    } finally {
      disconnectAllFromDS();
    }
  }

  @Test
  public void testGCForEntireCluster() {
    setupForGC();
    String command = "gc";
    CommandResult cmdResult = executeCommand(command);
    cmdResult.resetToFirstLine();
    if (cmdResult != null) {
      String cmdResultStr = commandResultToString(cmdResult);
      getLogWriter().info("testGCForEntireCluster cmdResultStr=" + cmdResultStr + "; cmdResult=" + cmdResult);
      assertEquals(Result.Status.OK, cmdResult.getStatus());
      if (cmdResult.getType().equals(ResultData.TYPE_TABULAR)) {
        TabularResultData table = (TabularResultData) cmdResult.getResultData();
        List<String> memberNames = table.retrieveAllValues(CliStrings.GC__MSG__MEMBER_NAME);
        assertEquals(3, memberNames.size());
      } else {
        fail("testGCForGroup failed as CommandResult should be table type");
      }
    } else {
      fail("testGCForGroup failed as did not get CommandResult");
    }
  }

  void setupForGC() {
    disconnectAllFromDS();

    final VM vm1 = Host.getHost(0).getVM(1);
    final VM vm2 = Host.getHost(0).getVM(2);


    setUpJmxManagerOnVm0ThenConnect(null);
    vm1.invoke(new SerializableRunnable() {
      public void run() {
        // no need to close cache as it will be closed as part of teardown2
        Cache cache = getCache();

        RegionFactory<Integer, Integer> dataRegionFactory = cache.createRegionFactory(RegionShortcut.PARTITION);
        Region region = dataRegionFactory.create("testRegion");
        for (int i = 0; i < 10; i++) {
          region.put("key" + (i + 200), "value" + (i + 200));
        }
      }
    });
    vm2.invoke(new SerializableRunnable() {
      public void run() {
        // no need to close cache as it will be closed as part of teardown2
        Cache cache = getCache();

        RegionFactory<Integer, Integer> dataRegionFactory = cache.createRegionFactory(RegionShortcut.PARTITION);
        dataRegionFactory.create("testRegion");
      }
    });
  }

  @Test
  public void testShutDownWithoutTimeout() {

    addIgnoredException("EntryDestroyedException");

    setupForShutDown();
    ThreadUtils.sleep(2500);

    String command = "shutdown";
    CommandResult cmdResult = executeCommand(command);

    if (cmdResult != null) {
      String cmdResultStr = commandResultToString(cmdResult);
      getLogWriter().info("testShutDownWithoutTimeout cmdResultStr=" + cmdResultStr);
    }

    verifyShutDown();

    final HeadlessGfsh defaultShell = getDefaultShell();

    // Need for the Gfsh HTTP enablement during shutdown to properly assess the
    // state of the connection.
    waitForCriterion(new WaitCriterion() {
      public boolean done() {
        return !defaultShell.isConnectedAndReady();
      }

      public String description() {
        return "Waits for the shell to disconnect!";
      }
    }, 1000, 250, true);

    assertFalse(defaultShell.isConnectedAndReady());
  }

  @Ignore("Disabled for 52350")
  @Test
  public void testShutDownWithTimeout() {
    setupForShutDown();
    ThreadUtils.sleep(2500);

    addIgnoredException("EntryDestroyedException");

    String command = "shutdown --time-out=15";
    CommandResult cmdResult = executeCommand(command);

    if (cmdResult != null) {
      String cmdResultStr = commandResultToString(cmdResult);
      getLogWriter().info("testShutDownWithTIMEOUT cmdResultStr=" + cmdResultStr);
    }

    verifyShutDown();

    final HeadlessGfsh defaultShell = getDefaultShell();

    // Need for the Gfsh HTTP enablement during shutdown to properly assess the state of the connection.
    waitForCriterion(new WaitCriterion() {
      public boolean done() {
        return !defaultShell.isConnectedAndReady();
      }

      public String description() {
        return "Waits for the shell to disconnect!";
      }
    }, 1000, 250, false);

    assertFalse(defaultShell.isConnectedAndReady());
  }

  @Test
  public void testShutDownForTIMEOUT() {
    setupForShutDown();
    ThreadUtils.sleep(2500);
    final VM vm0 = Host.getHost(0).getVM(0);
    vm0.invoke(new SerializableRunnable() {
      public void run() {
        System.setProperty("ThrowTimeoutException", "true");
      }
    });


    String command = "shutdown --time-out=15";
    CommandResult cmdResult = executeCommand(command);

    if (cmdResult != null) {
      String cmdResultStr = commandResultToString(cmdResult);
      getLogWriter().info("testShutDownForTIMEOUT cmdResultStr = " + cmdResultStr);
      CommandResult result = (CommandResult) ResultBuilder.createInfoResult(CliStrings.SHUTDOWN_TIMEDOUT);
      String expectedResult = commandResultToString(result);
      assertEquals(expectedResult, cmdResultStr);
    }
    vm0.invoke(new SerializableRunnable() {
      public void run() {
        System.clearProperty("ThrowTimeoutException");
      }
    });
  }

  void setupForChangeLogLelvel() {
    final VM vm0 = Host.getHost(0).getVM(0);
    final VM vm1 = Host.getHost(0).getVM(1);

    setUpJmxManagerOnVm0ThenConnect(null);
    vm1.invoke(new SerializableRunnable() {
      public void run() {
        // no need to close cache as it will be closed as part of teardown2
        Cache cache = getCache();

        RegionFactory<Integer, Integer> dataRegionFactory = cache.createRegionFactory(RegionShortcut.PARTITION);
        Region region = dataRegionFactory.create("testRegion");
        for (int i = 0; i < 10; i++) {
          region.put("key" + (i + 200), "value" + (i + 200));
        }
      }
    });
  }

  void setupForShutDown() {
    final VM vm0 = Host.getHost(0).getVM(0);
    final VM vm1 = Host.getHost(0).getVM(1);

    System.setProperty(CliStrings.IGNORE_INTERCEPTORS, "true");
    setUpJmxManagerOnVm0ThenConnect(null);
    vm1.invoke(new SerializableRunnable() {
      public void run() {
        // no need to close cache as it will be closed as part of teardown2
        Cache cache = getCache();

        RegionFactory<Integer, Integer> dataRegionFactory = cache.createRegionFactory(RegionShortcut.PARTITION);
        Region region = dataRegionFactory.create("testRegion");
        for (int i = 0; i < 10; i++) {
          region.put("key" + (i + 200), "value" + (i + 200));
        }
      }
    });
  }

  void verifyShutDown() {
    final VM vm0 = Host.getHost(0).getVM(0);
    final VM vm1 = Host.getHost(0).getVM(1);

    @SuppressWarnings("serial") final SerializableCallable connectedChecker = new SerializableCallable() {
      @Override
      public Object call() throws Exception {
        boolean cacheExists = true;
        try {
          Cache cacheInstance = CacheFactory.getAnyInstance();
          cacheExists = cacheInstance.getDistributedSystem().isConnected();
        } catch (CacheClosedException e) {
          cacheExists = false;
        }
        return cacheExists;
      }
    };

    WaitCriterion waitCriterion = new WaitCriterion() {
      @Override
      public boolean done() {
        return Boolean.FALSE.equals(vm0.invoke(connectedChecker)) && Boolean.FALSE.equals(vm1.invoke(connectedChecker));
      }

      @Override
      public String description() {
        return "Wait for gfsh to get disconnected from Manager.";
      }
    };
    waitForCriterion(waitCriterion, 5000, 200, true);

    assertTrue(Boolean.FALSE.equals(vm1.invoke(connectedChecker)));
    assertTrue(Boolean.FALSE.equals(vm0.invoke(connectedChecker)));
  }

  @Test
  public void testChangeLogLevelForMembers() {
    final VM vm0 = Host.getHost(0).getVM(0);
    final VM vm1 = Host.getHost(0).getVM(1);

    setupForChangeLogLelvel();

    String serverName1 = (String) vm0.invoke(new SerializableCallable() {
      @Override
      public Object call() throws Exception {
        cachedLogLevel = System.getProperty(DistributionConfig.GEMFIRE_PREFIX + "log-level");
        return GemFireCacheImpl.getInstance().getDistributedSystem().getDistributedMember().getId();
      }
    });

    String serverName2 = (String) vm1.invoke(new SerializableCallable() {
      @Override
      public Object call() throws Exception {
        cachedLogLevel = System.getProperty(DistributionConfig.GEMFIRE_PREFIX + "log-level");
        return GemFireCacheImpl.getInstance().getDistributedSystem().getDistributedMember().getId();
      }
    });

    String commandString = CliStrings.CHANGE_LOGLEVEL + " --" + CliStrings.CHANGE_LOGLEVEL__LOGLEVEL + "=finer" + " --" + CliStrings.CHANGE_LOGLEVEL__MEMBER + "=" + serverName1 + "," + serverName2;

    CommandResult commandResult = executeCommand(commandString);
    getLogWriter().info("testChangeLogLevel commandResult=" + commandResult);
    assertTrue(Status.OK.equals(commandResult.getStatus()));
    CompositeResultData resultData = (CompositeResultData) commandResult.getResultData();
    SectionResultData section = resultData.retrieveSection("section");
    assertNotNull(section);
    TabularResultData tableRsultData = section.retrieveTable("ChangeLogLevel");
    assertNotNull(tableRsultData);

    List<String> columns = tableRsultData.retrieveAllValues(CliStrings.CHANGE_LOGLEVEL__COLUMN_MEMBER);
    List<String> status = tableRsultData.retrieveAllValues(CliStrings.CHANGE_LOGLEVEL__COLUMN_STATUS);

    assertEquals(columns.size(), 2);
    assertEquals(status.size(), 2);

    assertTrue(columns.contains(serverName1));
    assertTrue(columns.contains(serverName2));
    assertTrue(status.contains("true"));
  }

  @Test
  public void testChangeLogLevelForGrps() {
    Properties localProps = new Properties();
    localProps.setProperty(NAME, "Manager");
    localProps.setProperty(GROUPS, "Group0");

    final VM vm1 = Host.getHost(0).getVM(1);
    final VM vm2 = Host.getHost(0).getVM(2);
    final String grp1 = "Group1";
    final String grp2 = "Group2";

    setUpJmxManagerOnVm0ThenConnect(localProps);

    String vm1id = (String) vm1.invoke(new SerializableCallable() {
      @Override
      public Object call() throws Exception {
        Properties localProps = new Properties();
        localProps.setProperty(GROUPS, grp1);
        getSystem(localProps);
        Cache cache = getCache();
        return cache.getDistributedSystem().getDistributedMember().getId();
      }
    });

    String vm2id = (String) vm2.invoke(new SerializableCallable() {
      @Override
      public Object call() throws Exception {
        Properties localProps = new Properties();
        localProps.setProperty(GROUPS, grp2);
        getSystem(localProps);
        Cache cache = getCache();
        return cache.getDistributedSystem().getDistributedMember().getId();
      }
    });

    String commandString = CliStrings.CHANGE_LOGLEVEL + " --" + CliStrings.CHANGE_LOGLEVEL__LOGLEVEL + "=finer" + " --" + CliStrings.CHANGE_LOGLEVEL__GROUPS + "=" + grp1 + "," + grp2;

    CommandResult commandResult = executeCommand(commandString);
    getLogWriter().info("testChangeLogLevelForGrps commandResult=" + commandResult);

    assertTrue(Status.OK.equals(commandResult.getStatus()));

    CompositeResultData resultData = (CompositeResultData) commandResult.getResultData();
    SectionResultData section = resultData.retrieveSection("section");
    assertNotNull(section);
    TabularResultData tableRsultData = section.retrieveTable("ChangeLogLevel");
    assertNotNull(tableRsultData);

    List<String> columns = tableRsultData.retrieveAllValues(CliStrings.CHANGE_LOGLEVEL__COLUMN_MEMBER);
    List<String> status = tableRsultData.retrieveAllValues(CliStrings.CHANGE_LOGLEVEL__COLUMN_STATUS);

    assertEquals(columns.size(), 2);
    assertEquals(status.size(), 2);

    assertTrue(columns.contains(vm1id));
    assertTrue(columns.contains(vm2id));
    assertTrue(status.contains("true"));
  }
}
