package org.jboss.ddoyle.drools.sample;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.drools.core.time.impl.PseudoClockScheduler;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PhreakyTest {

	private static final String OUT_CHANNEL_NAME = "out";

	private static KieContainer kieContainer;
	private KieSession kieSession;
	private MockChannel channel;

	@BeforeClass
	public static void initKieContainer() {
		KieServices kieServices = KieServices.Factory.get();
		kieContainer = kieServices.getKieClasspathContainer();
	}

	/*
	 * Load and initialize a new KieSession on every test.
	 */
	@Before
	public void initKieSession() {
		kieSession = kieContainer.newKieSession();
		channel = new MockChannel();
		kieSession.getChannels().put(OUT_CHANNEL_NAME, channel);
		AgendaEventListener myAgendaEventListener = new MyAgendaEventListener();
		kieSession.addEventListener(myAgendaEventListener);
		Logger rulesLogger = LoggerFactory.getLogger("DroolsLogger");
		kieSession.setGlobal("LOGGER", rulesLogger);
	}
	
	@Test
	public void testDynamicOrdering() {
		PseudoClockScheduler pseudoClock = (PseudoClockScheduler) kieSession.getSessionClock();
		pseudoClock.setStartupTime(0);
		//Insert the first 5, 4 should be filtered, the fifth should be sent
		int fq = 40;
		for (int i = 0; i < fq; i++) {
			insertEvent("K08", SimpleEvent.Status.ENRICHED, new Date(i), i);
			long advanceTime = i - pseudoClock.getCurrentTime();
			if (advanceTime > 0) {
				pseudoClock.advanceTime(advanceTime, TimeUnit.MILLISECONDS);
			}
		}
		kieSession.fireAllRules();
		
		List<Object> sentObjects = channel.getSentObject();
		//List should contain objects with Index: 4, 9, 14, 19, 24, 29, 34 and 39.
		List<Long> indexes = new ArrayList<Long>();
		indexes.add(4L);
		indexes.add(9L);
		indexes.add(14L);
		indexes.add(19L);
		indexes.add(24L);
		indexes.add(29L);
		indexes.add(34L);
		indexes.add(39L);
		
		for (Object nextObject: sentObjects) {
			if (! (nextObject instanceof ChannelData)) {
				fail("Expected an object of type ChannelData.");
			}
			Object data = ((ChannelData) nextObject).getData();
			if (! (data instanceof SimpleEvent)) {
				fail("Expected an object of type SimpleEvent.");
			}
			SimpleEvent nextEvent = (SimpleEvent) data;
			long eventIndex = nextEvent.getIndex();
			assertTrue("An unexpected SimpleEvent instance was sent to the output channel. SimpleEvent with index '" + eventIndex + "' is not expected.", indexes.contains(eventIndex));
			indexes.remove(eventIndex);
		}
		assertTrue("Not all expected SimpleEvents have been sent to the output channel. The following events have not been sent to the output channel: " + indexes.toString(), indexes.isEmpty());
	}
		
	private SimpleEvent createEvent(final String code, final SimpleEvent.Status status, final Date timestamp, final int index) {
		SimpleEvent event = new SimpleEvent();
		event.setCode(code);
		event.setStatus(status);
		event.setEventDate(timestamp);
		event.setIndex(index);
		return event;
	}

	protected void insertEvent(final String code, final SimpleEvent.Status status, final Date timestamp, final int index) {
		kieSession.insert(createEvent(code, status, timestamp, index));
	}

}
