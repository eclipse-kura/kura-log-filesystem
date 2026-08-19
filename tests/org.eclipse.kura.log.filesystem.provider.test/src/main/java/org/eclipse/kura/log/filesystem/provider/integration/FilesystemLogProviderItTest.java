/*******************************************************************************
 * Copyright (c) 2026 Eurotech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Eurotech
 *******************************************************************************/
package org.eclipse.kura.log.filesystem.provider.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.kura.configuration.ConfigurationService;
import org.eclipse.kura.log.LogEntry;
import org.eclipse.kura.log.LogProvider;
import org.eclipse.kura.log.listener.LogListener;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies that the Filesystem log provider bundle resolves and works in a real
 * Kura framework: an instance created through the Configuration Service
 * publishes a {@link LogProvider} service which tails the configured file and
 * hands the parsed entries to its listeners.
 *
 * The services are looked up through the {@link BundleContext} instead of being
 * injected with {@code @Reference}: the Kura bundles carry no
 * {@code osgi.service} capability, so a Declarative Services reference to them
 * would make the bnd resolution of this bndrun fail.
 */
@Component(immediate = true)
public class FilesystemLogProviderItTest {

    private static final Logger logger = LoggerFactory.getLogger(FilesystemLogProviderItTest.class);

    private static final String FACTORY_PID = "org.eclipse.kura.log.filesystem.provider.FilesystemLogProvider";
    private static final String PROVIDER_PID = "testFilesystemLogProvider";

    private static final String LOG_LINE = "2026-08-17T11:00:00,000 [main] INFO  o.e.k.i.Test - integration test line";

    private static final long TIMEOUT_SECONDS = 60;

    private static final CountDownLatch activated = new CountDownLatch(1);

    // needs to be static for being available to JUnit Runner
    private static BundleContext bundleContext;
    private static ConfigurationService configurationService;
    private static LogProvider logProvider;
    private static Path logDir;
    private static Path logFile;

    @Activate
    public void activate(BundleContext context) {
        bundleContext = context;
        activated.countDown();
    }

    @BeforeClass
    public static void createProviderInstance() throws Exception {
        if (!activated.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("test component not activated in " + TIMEOUT_SECONDS + " seconds");
        }

        configurationService = awaitService(ConfigurationService.class, null);

        // the reader tails the file from its beginning, so it has to exist and
        // be empty when the provider starts. The file has to be named kura.log:
        // KuraLogLineParser only parses the Kura log format when the path says
        // so, and falls back to the raw line otherwise.
        logDir = Files.createTempDirectory("kura-log-filesystem-it");
        logFile = Files.createFile(logDir.resolve("kura.log"));

        final Map<String, Object> properties = new HashMap<>();
        properties.put("logFilePath", logFile.toString());

        logger.info("Creating {} instance {} on {}...", FACTORY_PID, PROVIDER_PID, logFile);
        configurationService.createFactoryConfiguration(FACTORY_PID, PROVIDER_PID, properties, false);

        logProvider = awaitService(LogProvider.class, "(kura.service.pid=" + PROVIDER_PID + ")");
    }

    @AfterClass
    public static void cleanup() {
        try {
            configurationService.deleteFactoryConfiguration(PROVIDER_PID, false);
        } catch (Exception e) {
            logger.warn("Error deleting the LogProvider instance", e);
        }

        try {
            Files.deleteIfExists(logFile);
            Files.deleteIfExists(logDir);
        } catch (IOException e) {
            logger.warn("Error deleting the temporary log file", e);
        }

        logger.info("Shutting down OSGi framework...");
        if (bundleContext != null) {
            try {
                Bundle systemBundle = bundleContext.getBundle(0);
                systemBundle.stop();
            } catch (BundleException e) {
                logger.error("Error stopping framework", e);
            }
        }
    }

    @Test
    public void shouldRegisterTheProviderFactory() throws Exception {
        assertTrue(configurationService.getFactoryComponentPids().contains(FACTORY_PID));
    }

    @Test
    public void shouldPublishTheLogProviderService() {
        assertNotNull(logProvider);
    }

    @Test
    public void shouldDeliverAppendedLinesToListeners() throws Exception {
        final BlockingQueue<LogEntry> received = new ArrayBlockingQueue<>(16);
        final LogListener listener = received::offer;

        logProvider.registerLogListener(listener);
        try {
            appendLine(LOG_LINE);

            final LogEntry entry = received.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertNotNull("no log entry delivered in " + TIMEOUT_SECONDS + " seconds", entry);
            assertEquals("main", entry.getProperties().get("_PID"));
            assertEquals("INFO", entry.getProperties().get("PRIORITY"));
            assertEquals("o.e.k.i.Test - integration test line", entry.getProperties().get("MESSAGE"));
            assertEquals(logFile.toString(), entry.getProperties().get("_TRANSPORT"));
        } finally {
            logProvider.unregisterLogListener(listener);
        }
    }

    private static void appendLine(final String line) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(logFile.toFile(), "rw")) {
            file.seek(file.length());
            file.write((line + System.lineSeparator()).getBytes());
        }
    }

    private static <T> T awaitService(final Class<T> clazz, final String filter) throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);

        while (System.nanoTime() < deadline) {
            final Collection<ServiceReference<T>> references = bundleContext.getServiceReferences(clazz, filter);
            if (!references.isEmpty()) {
                return bundleContext.getService(references.iterator().next());
            }
            Thread.sleep(500);
        }

        throw new IllegalStateException("no " + clazz.getSimpleName() + " service matching " + filter + " in "
                + TIMEOUT_SECONDS + " seconds");
    }

}
