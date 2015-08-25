package com.gemstone.gemfire.test.dunit;

import static com.gemstone.gemfire.test.dunit.DUnitEnv.getAllDistributedSystemProperties;
import static com.gemstone.gemfire.test.dunit.Invoke.invokeInEveryVM;
import static com.gemstone.gemfire.test.dunit.Invoke.invokeInLocator;
import static org.junit.Assert.assertEquals;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.Logger;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.gemstone.gemfire.admin.internal.AdminDistributedSystemImpl;
import com.gemstone.gemfire.cache.hdfs.internal.hoplog.HoplogConfig;
import com.gemstone.gemfire.cache.query.QueryTestUtils;
import com.gemstone.gemfire.cache.query.internal.QueryObserverHolder;
import com.gemstone.gemfire.cache30.GlobalLockingDUnitTest;
import com.gemstone.gemfire.cache30.MultiVMRegionTestCase;
import com.gemstone.gemfire.cache30.RegionTestCase;
import com.gemstone.gemfire.distributed.DistributedSystem;
import com.gemstone.gemfire.distributed.internal.DistributionConfig;
import com.gemstone.gemfire.distributed.internal.DistributionMessageObserver;
import com.gemstone.gemfire.distributed.internal.InternalDistributedSystem;
import com.gemstone.gemfire.distributed.internal.InternalDistributedSystem.CreationStackGenerator;
import com.gemstone.gemfire.internal.AvailablePort;
import com.gemstone.gemfire.internal.InternalDataSerializer;
import com.gemstone.gemfire.internal.InternalInstantiator;
import com.gemstone.gemfire.internal.SocketCreator;
import com.gemstone.gemfire.internal.admin.ClientStatsManager;
import com.gemstone.gemfire.internal.cache.GemFireCacheImpl;
import com.gemstone.gemfire.internal.cache.InitialImageOperation;
import com.gemstone.gemfire.internal.cache.tier.InternalBridgeMembership;
import com.gemstone.gemfire.internal.cache.tier.sockets.CacheServerTestUtil;
import com.gemstone.gemfire.internal.cache.tier.sockets.ClientProxyMembershipID;
import com.gemstone.gemfire.internal.cache.tier.sockets.DataSerializerPropogationDUnitTest;
import com.gemstone.gemfire.internal.logging.InternalLogWriter;
import com.gemstone.gemfire.internal.logging.LogService;
import com.gemstone.gemfire.internal.logging.log4j.LogWriterLogger;
import com.gemstone.gemfire.management.internal.cli.LogWrapper;
import com.gemstone.gemfire.test.dunit.standalone.DUnitLauncher;
import com.gemstone.org.jgroups.stack.IpAddress;
import com.gemstone.org.jgroups.stack.Protocol;
import com.gemstone.org.jgroups.util.GemFireTracer;

@SuppressWarnings("serial")
public class DUnitTestRule implements TestRule, Serializable {

  private static volatile String testClassName;
  private static volatile String testMethodName;
  
  private volatile String className;
  private volatile String methodName;
  
  private final DUnitTestCase distTestCase;
  
  protected DUnitTestRule(Builder builder) {
    this.distTestCase = new DUnitTestCase();
  }
  
  public DUnitTestRule() {
    this.distTestCase = new DUnitTestCase();
  }
  
  @Override
  public Statement apply(final Statement base, final Description description) {
    starting(description);
    return statement(base);
  }
  
  /**
   * Invoked when a test is about to start
   */
  protected void starting(Description description) {
    this.className = description.getClassName();
    this.methodName = description.getMethodName();
    
    testClassName = this.className;
    testMethodName = this.methodName;
  }
  
  protected void before() throws Throwable {
    DUnitLauncher.launchIfNeeded();
    
    this.distTestCase.setUpDistributedTestCase();
  }

  protected void after() throws Throwable {
    try {
      this.distTestCase.tearDownDistributedTestCase();
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  
  public String getClassName() {
    return this.className;
  }
  
  public String getMethodName() {
    return this.methodName;
  }
  
  public final String getUniqueName() {
    return testClassName + "_" + testMethodName;
  }
  
  public static String getTestClassName() {
    return testClassName;
  }
  
  public static String getTestMethodName() {
    return testMethodName;
  }
  
  private Statement statement(final Statement base) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        before();
        try {
          base.evaluate();
        } finally {
          after();
        }
      }
    };
  }
  
  public static class Builder {
    protected Builder() {}

    public Builder vmCount(final int vmCount) {
      return this;
    }
    
    public Builder logPerTest(final boolean logPerTest) {
      return this;
    }
    
    public Builder logPerTestClass(final boolean logPerTestClass) {
      return this;
    }
    
    public DUnitTestRule build() {
      return new DUnitTestRule(this);
    }
  }
  
  public static class DUnitTestCase implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LogService.getLogger();
    private static final LogWriterLogger oldLogger = LogWriterLogger.create(logger);

    private static InternalDistributedSystem system;
    protected static String previousSystemCreatedInTestClass;
    protected static Properties previousProperties;
    
    protected volatile boolean logPerTest = Boolean.getBoolean("dunitLogPerTest");

    /**
     * Creates a new <code>DistributedTestCase</code> test.
     */
    public DUnitTestCase() {
      DUnitLauncher.launchIfNeeded();
    }

    //---------------------------------------------------------------------------
    // setUp methods
    //---------------------------------------------------------------------------
    
    public final void setUpDistributedTestCase() throws Exception {
      setUpCreationStackGenerator();
      
      System.setProperty(HoplogConfig.ALLOW_LOCAL_HDFS_PROP, "true");
      GemFireCacheImpl.setDefaultDiskStoreName(getDefaultDiskStoreName()); // TODO: not thread safe
      
      for (int h = 0; h < Host.getHostCount(); h++) {
        Host host = Host.getHost(h);
        for (int v = 0; v < host.getVMCount(); v++) {
          VM vm = host.getVM(v);
          final String vmDefaultDiskStoreName = "DiskStore-" + h + "-" + v + "-" + testClassName + "." + testMethodName;
          setUpInVM(vm, testClassName, testMethodName, vmDefaultDiskStoreName);
        }
      }
      //System.out.println("\n\n[setup] START TEST " + getClass().getSimpleName()+"."+testName+"\n\n");
    }

    private static void setUpInVM(final VM vm, final String testClassNameToUse, final String testMethodNameToUse, final String diskStoreNameToUse) {
      vm.invoke(new SerializableRunnable() {
        private static final long serialVersionUID = 1L;

        @Override
        public void run() {
          setUpCreationStackGenerator();
          testClassName = testClassNameToUse;
          testMethodName = testMethodNameToUse;
          System.setProperty(HoplogConfig.ALLOW_LOCAL_HDFS_PROP, "true");    
          GemFireCacheImpl.setDefaultDiskStoreName(diskStoreNameToUse); // TODO: not thread safe
        }
      });
    }
    
    //---------------------------------------------------------------------------
    // tearDown methods
    //---------------------------------------------------------------------------
    
    /**
     * For logPerTest to work, we have to disconnect from the DS, but all
     * subclasses do not call super.tearDown(). To prevent this scenario
     * this method has been declared final. Subclasses must now override
     * {@link #tearDownBefore()} instead.
     * @throws Exception
     */
    public final void tearDownDistributedTestCase() throws Exception {
      tearDownBefore();
      realTearDown();
      tearDownAfter();
      
      tearDownCreationStackGenerator();

      tearDownInEveryVM();
    }

    private static void tearDownInEveryVM() {
      invokeInEveryVM(new SerializableRunnable() {
        private static final long serialVersionUID = 1L;

        @Override
        public void run() {    
          tearDownCreationStackGenerator();
          testClassName = null;
          testMethodName = null;
        }
      });
    }
    
    protected void realTearDown() throws Exception {
      if (logPerTest) {
        disconnectFromDS();
        invokeInEveryVM(DistributedTestCase.class, "disconnectFromDS");
      }
      cleanupAllVms();
    }
    
    /**
     * Tears down the test. This method is called by the final {@link #tearDown()} method and should be overridden to
     * perform actual test cleanup and release resources used by the test.  The tasks executed by this method are
     * performed before the DUnit test framework using Hydra cleans up the client VMs.
     * <p/>
     * @throws Exception if the tear down process and test cleanup fails.
     * @see #tearDown
     * @see #tearDownAfter()
     */
    protected void tearDownBefore() throws Exception {
    }

    /**
     * Tears down the test.  Performs additional tear down tasks after the DUnit tests framework using Hydra cleans up
     * the client VMs.  This method is called by the final {@link #tearDown()} method and should be overridden to perform
     * post tear down activities.
     * <p/>
     * @throws Exception if the test tear down process fails.
     * @see #tearDown()
     * @see #tearDownBefore()
     */
    protected void tearDownAfter() throws Exception {
    }

    //---------------------------------------------------------------------------
    // test name methods
    //---------------------------------------------------------------------------
    
    /**
     * Returns a unique name for this test method.  It is based on the
     * name of the class as well as the name of the method.
     */
    public final String getUniqueName() { // TODO: consider using FQCN
      return testClassName + "_" + testMethodName;
    }

    protected static String getTestName() {
      return testMethodName;
    }

    //---------------------------------------------------------------------------
    // public final methods
    //---------------------------------------------------------------------------
    
    /**
     * Returns this VM's connection to the distributed system.  If
     * necessary, the connection will be lazily created using the given
     * <code>Properties</code>.  Note that this method uses hydra's
     * configuration to determine the location of log files, etc.
     * Note: "final" was removed so that WANTestBase can override this method.
     * This was part of the xd offheap merge.
     *
     * @see hydra.DistributedConnectionMgr#connect
     * @since 3.0
     */
    public final InternalDistributedSystem getSystem(Properties properties) {
      if (system == null) {
        system = InternalDistributedSystem.getAnyInstance();
      }
      
      if (system == null || !system.isConnected()) {
        // there is no previous system yet
        final Properties newProperties = getAllDistributedSystemProperties(properties);
        previousSystemCreatedInTestClass = getTestClassName();
        if (logPerTest) {
          newProperties.put(DistributionConfig.LOG_FILE_NAME, getUniqueName() + ".log");
          newProperties.put(DistributionConfig.STATISTIC_ARCHIVE_FILE_NAME, getUniqueName() + ".gfs");
        }
        system = (InternalDistributedSystem)DistributedSystem.connect(newProperties);
        previousProperties = newProperties;
        
      } else {
        // there is a previous system
        boolean needNewSystem = false;
        //if (!getUniqueName().equals(previousTestName)) {
        if (!getTestClassName().equals(previousSystemCreatedInTestClass)) {
          // previous system was created in a previous test class
          final Properties newProperties = getAllDistributedSystemProperties(properties);
          needNewSystem = !newProperties.equals(previousProperties);
          if (needNewSystem) {
            logger.info(
                "Test class has changed and the new DS properties are not an exact match. "
                    + "Forcing DS disconnect. Old props = "
                    + previousProperties + "new props=" + newProperties);
          }
          
        } else {
          // previous system was created in this test class
          final Properties currentProperties = system.getProperties();
          for (Iterator iter = properties.entrySet().iterator(); iter.hasNext(); ) {
            final Map.Entry entry = (Map.Entry) iter.next();
            final String key = (String) entry.getKey();
            final String value = (String) entry.getValue();
            if (!value.equals(currentProperties.getProperty(key))) {
              needNewSystem = true;
              logger.info("Forcing DS disconnect. For property " + key
                                  + " old value = " + currentProperties.getProperty(key)
                                  + " new value = " + value);
              break;
            }
          }
        }
        
        if (needNewSystem) {
          // the current system does not meet our needs to disconnect and
          // call recursively to get a new system.
          logger.info("Disconnecting from current DS in order to make a new one");
          disconnectFromDS();
          getSystem(properties);
        }
      }
      return system;
    }
    
    /**
     * Returns this VM's connection to the distributed system.  If
     * necessary, the connection will be lazily created using the
     * <code>Properties</code> returned by {@link
     * #getDistributedSystemProperties}.
     *
     * @see #getSystem(Properties)
     *
     * @since 3.0
     */
    public final InternalDistributedSystem getSystem() {
      return getSystem(this.getDistributedSystemProperties());
    }

    /**
     * Returns a loner distributed system that isn't connected to other
     * vms
     * 
     * @since 6.5
     */
    public final InternalDistributedSystem getLonerSystem() {
      Properties props = this.getDistributedSystemProperties();
      props.put(DistributionConfig.MCAST_PORT_NAME, "0");
      props.put(DistributionConfig.LOCATORS_NAME, "");
      return getSystem(props);
    }
    
    /**
     * Returns a loner distributed system in combination with enforceUniqueHost
     * and redundancyZone properties.
     * Added specifically to test scenario of defect #47181.
     */
    public final InternalDistributedSystem getLonerSystemWithEnforceUniqueHost() {
      Properties props = this.getDistributedSystemProperties();
      props.put(DistributionConfig.MCAST_PORT_NAME, "0");
      props.put(DistributionConfig.LOCATORS_NAME, "");
      props.put(DistributionConfig.ENFORCE_UNIQUE_HOST_NAME, "true");
      props.put(DistributionConfig.REDUNDANCY_ZONE_NAME, "zone1");
      return getSystem(props);
    }

    /**
     * Returns an mcast distributed system that is connected to other
     * vms using a random mcast port.
     */
    public final InternalDistributedSystem getMcastSystem() {
      Properties props = this.getDistributedSystemProperties();
      int port = AvailablePort.getRandomAvailablePort(AvailablePort.JGROUPS);
      props.put(DistributionConfig.MCAST_PORT_NAME, ""+port);
      props.put(DistributionConfig.MCAST_TTL_NAME, "0");
      props.put(DistributionConfig.LOCATORS_NAME, "");
      return getSystem(props);
    }

    /**
     * Returns an mcast distributed system that is connected to other
     * vms using the given mcast port.
     */
    public final InternalDistributedSystem getMcastSystem(int jgroupsPort) {
      Properties props = this.getDistributedSystemProperties();
      props.put(DistributionConfig.MCAST_PORT_NAME, ""+jgroupsPort);
      props.put(DistributionConfig.MCAST_TTL_NAME, "0");
      props.put(DistributionConfig.LOCATORS_NAME, "");
      return getSystem(props);
    }

    /**
     * Returns whether or this VM is connected to a {@link
     * DistributedSystem}.
     */
    public final boolean isConnectedToDS() {
      return system != null && system.isConnected();
    }

    //---------------------------------------------------------------------------
    // public methods
    //---------------------------------------------------------------------------
    
    /**
     * Returns a <code>Properties</code> object used to configure a
     * connection to a {@link
     * com.gemstone.gemfire.distributed.DistributedSystem}.
     * Unless overridden, this method will return an empty
     * <code>Properties</code> object.
     *
     * @since 3.0
     */
    public Properties getDistributedSystemProperties() {
      return new Properties();
    }

    //---------------------------------------------------------------------------
    // private
    //---------------------------------------------------------------------------

    private String getDefaultDiskStoreName() { // TODO: move
      String vmid = System.getProperty("vmid");
      return "DiskStore-"  + vmid + "-"+ testClassName + "." + getTestName();
    }

    //---------------------------------------------------------------------------
    // deprecated static methods
    //---------------------------------------------------------------------------
    
    /**
     * Returns a <code>LogWriter</code> for logging information
     * @deprecated Use a static logger from the log4j2 LogService.getLogger instead.
     */
    @Deprecated
    public static InternalLogWriter getLogWriter() { // TODO: delete
      return oldLogger;
    }

    //---------------------------------------------------------------------------
    // private static methods
    //---------------------------------------------------------------------------
    
    private static void setUpCreationStackGenerator() {
      // the following is moved from InternalDistributedSystem to fix #51058
      InternalDistributedSystem.TEST_CREATION_STACK_GENERATOR.set(
      new CreationStackGenerator() {
        @Override
        public Throwable generateCreationStack(final DistributionConfig config) {
          final StringBuilder sb = new StringBuilder();
          final String[] validAttributeNames = config.getAttributeNames();
          for (int i = 0; i < validAttributeNames.length; i++) {
            final String attName = validAttributeNames[i];
            final Object actualAtt = config.getAttributeObject(attName);
            String actualAttStr = actualAtt.toString();
            sb.append("  ");
            sb.append(attName);
            sb.append("=\"");
            if (actualAtt.getClass().isArray()) {
              actualAttStr = InternalDistributedSystem.arrayToString(actualAtt);
            }
            sb.append(actualAttStr);
            sb.append("\"");
            sb.append("\n");
          }
          return new Throwable("Creating distributed system with the following configuration:\n" + sb.toString());
        }
      });
    }
    
    private static void tearDownCreationStackGenerator() {
      InternalDistributedSystem.TEST_CREATION_STACK_GENERATOR.set(InternalDistributedSystem.DEFAULT_CREATION_STACK_GENERATOR);
    }
    
    //---------------------------------------------------------------------------
    // tearDown methods
    //---------------------------------------------------------------------------
    
    public static void cleanupAllVms() {
      cleanupThisVM();
      invokeInEveryVM(DistributedTestCase.class, "cleanupThisVM");
      invokeInLocator(new SerializableRunnable() {
        private static final long serialVersionUID = 1L;

        @Override
        public void run() {
          DistributionMessageObserver.setInstance(null);
          unregisterInstantiatorsInThisVM();
        }
      });
      DUnitLauncher.closeAndCheckForSuspects();
    }

    public static void unregisterAllDataSerializersFromAllVms() {
      unregisterDataSerializerInThisVM();
      invokeInEveryVM(new SerializableRunnable() {
        private static final long serialVersionUID = 1L;

        @Override
        public void run() {
          unregisterDataSerializerInThisVM();
        }
      });
      invokeInLocator(new SerializableRunnable() {
        private static final long serialVersionUID = 1L;

        @Override
        public void run() {
          unregisterDataSerializerInThisVM();
        }
      });
    }

    public static void unregisterInstantiatorsInThisVM() {
      // unregister all the instantiators
      InternalInstantiator.reinitialize();
      assertEquals(0, InternalInstantiator.getInstantiators().length);
    }
    
    public static void unregisterDataSerializerInThisVM() {
      DataSerializerPropogationDUnitTest.successfullyLoadedTestDataSerializer = false;
      // unregister all the Dataserializers
      InternalDataSerializer.reinitialize();
      // ensure that all are unregistered
      assertEquals(0, InternalDataSerializer.getSerializers().length);
    }

    protected static void disconnectAllFromDS() {
      disconnectFromDS();
      invokeInEveryVM(DistributedTestCase.class, "disconnectFromDS");
    }

    /**
     * Disconnects this VM from the distributed system
     */
    public static void disconnectFromDS() {
      GemFireCacheImpl.testCacheXml = null;
      if (system != null) {
        system.disconnect();
        system = null;
      }
      
      for (;;) {
        DistributedSystem ds = InternalDistributedSystem.getConnectedInstance();
        if (ds == null) {
          break;
        }
        try {
          ds.disconnect();
        }
        catch (Exception e) {
          // ignore
        }
      }
      
      AdminDistributedSystemImpl ads = AdminDistributedSystemImpl.getConnectedInstance();
      if (ads != null) {// && ads.isConnected()) {
        ads.disconnect();
      }
    }

    private static void cleanupThisVM() {
      IpAddress.resolve_dns = true;
      SocketCreator.resolve_dns = true;
      InitialImageOperation.slowImageProcessing = 0;
      DistributionMessageObserver.setInstance(null);
      QueryTestUtils.setCache(null);
      CacheServerTestUtil.clearCacheReference();
      RegionTestCase.preSnapshotRegion = null;
      GlobalLockingDUnitTest.region_testBug32356 = null;
      LogWrapper.close();
      ClientProxyMembershipID.system = null;
      MultiVMRegionTestCase.CCRegion = null;
      InternalBridgeMembership.unregisterAllListeners();
      ClientStatsManager.cleanupForTests();
      unregisterInstantiatorsInThisVM();
      GemFireTracer.DEBUG = Boolean.getBoolean("DistributionManager.DEBUG_JAVAGROUPS");
      Protocol.trace = GemFireTracer.DEBUG;
      DistributionMessageObserver.setInstance(null);
      QueryObserverHolder.reset();
      if (InternalDistributedSystem.systemAttemptingReconnect != null) {
        InternalDistributedSystem.systemAttemptingReconnect.stopReconnecting();
      }
      ExpectedExceptionString ex;
      while((ex = ExpectedExceptionString.poll()) != null) {
        ex.remove();
      }
    }
  }
}