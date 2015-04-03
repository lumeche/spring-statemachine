package demo.cdplayer;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.statemachine.EnumStateMachine;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineSystemConstants;
import org.springframework.statemachine.listener.StateMachineListener;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.transition.Transition;

import demo.CommonConfiguration;
import demo.cdplayer.Application.Events;
import demo.cdplayer.Application.States;

public class CdPlayerTests {

	private AnnotationConfigApplicationContext context;

	private StateMachine<States,Events> machine;

	private CdPlayer player;

	private Library library;

	private TestListener listener;

	@Test
	public void testInitialState() throws InterruptedException {
		listener.stateChangedLatch.await(1, TimeUnit.SECONDS);
		assertThat(listener.stateChangedCount, is(2));
		assertThat(machine.getState().getIds(), contains(States.IDLE, States.CLOSED));
		assertLcdStatusStartsWith("No CD");
	}

	@Test
	public void testEjectTwice() throws Exception {
		listener.reset(1, 0, 0);
		player.eject();
		listener.stateChangedLatch.await(1, TimeUnit.SECONDS);
		assertThat(listener.stateChangedCount, is(1));
		assertThat(machine.getState().getIds(), contains(States.IDLE, States.OPEN));
		listener.reset(1, 0, 0);
		player.eject();
		listener.stateChangedLatch.await(1, TimeUnit.SECONDS);
		assertThat(listener.stateChangedCount, is(1));
		assertThat(machine.getState().getIds(), contains(States.IDLE, States.CLOSED));
	}

	@Test
	public void testPlayWithCdLoaded() throws Exception {
		listener.reset(4, 0, 0);
		player.eject();
		player.load(library.getCollection().get(0));
		player.eject();
		player.play();
		listener.stateChangedLatch.await(5, TimeUnit.SECONDS);
		assertThat(listener.stateChangedCount, is(4));
		assertThat(machine.getState().getIds(), contains(States.BUSY, States.PLAYING));
		assertLcdStatusContains("cd1");
	}

	@Test
	public void testPlayWithNoCdLoaded() throws Exception {
		listener.reset(0, 0, 0);
		player.play();
		listener.stateChangedLatch.await(1, TimeUnit.SECONDS);
		assertThat(listener.stateChangedCount, is(0));
		assertThat(machine.getState().getIds(), contains(States.IDLE, States.CLOSED));
		assertLcdStatusStartsWith("No CD");
	}

	@Test
	public void testPlayLcdTimeChanges() throws Exception {
		listener.reset(4, 0, 0);
		player.eject();
		player.load(library.getCollection().get(0));
		player.eject();
		player.play();
		listener.stateChangedLatch.await(1, TimeUnit.SECONDS);
		assertThat(listener.stateChangedCount, is(4));
		assertThat(machine.getState().getIds(), contains(States.BUSY, States.PLAYING));
		assertLcdStatusContains("cd1");

		listener.reset(0, 0, 0, 1);
		listener.transitionLatch.await(1, TimeUnit.SECONDS);
		assertThat(listener.transitionCount, is(1));
		assertLcdStatusContains("00:01");

		listener.reset(0, 0, 0, 1);
		listener.transitionLatch.await(1, TimeUnit.SECONDS);
		assertLcdStatusContains("00:02");
		assertThat(listener.transitionCount, is(1));

		listener.reset(0, 0, 0, 2);
		listener.transitionLatch.await(3, TimeUnit.SECONDS);
		assertThat(listener.transitionCount, is(2));
		assertLcdStatusContains("00:04");
	}

	@Test
	public void testPlayPause() throws Exception {
		listener.reset(4, 0, 0);
		player.eject();
		player.load(library.getCollection().get(0));
		player.eject();
		player.play();
		listener.stateChangedLatch.await(2, TimeUnit.SECONDS);
		assertThat(listener.stateChangedCount, is(4));
		assertThat(machine.getState().getIds(), contains(States.BUSY, States.PLAYING));
		assertLcdStatusContains("cd1");

		listener.reset(0, 0, 0, 1);
		listener.transitionLatch.await(2, TimeUnit.SECONDS);
		assertThat(listener.transitionCount, is(1));
		assertLcdStatusContains("00:01");

		listener.reset(0, 0, 0, 1);
		listener.transitionLatch.await(2, TimeUnit.SECONDS);
		assertLcdStatusContains("00:02");
		assertThat(listener.transitionCount, is(1));


		listener.reset(1, 0, 0, 0);
		player.pause();
		listener.stateChangedLatch.await(2, TimeUnit.SECONDS);
		assertThat(listener.stateChangedCount, is(1));
		assertLcdStatusContains("00:02");

		listener.reset(1, 0, 0, 1);
		player.pause();
		listener.stateChangedLatch.await(2, TimeUnit.SECONDS);
		listener.transitionLatch.await(2, TimeUnit.SECONDS);
		assertThat(listener.stateChangedCount, is(1));
		assertThat(listener.transitionCount, is(1));
		assertLcdStatusContains("00:03");

		listener.reset(0, 0, 0, 2);
		listener.transitionLatch.await(2, TimeUnit.SECONDS);
		assertThat(listener.transitionCount, is(2));
		assertLcdStatusContains("00:05");
	}

	@Test
	public void testPlayStop() throws Exception {
		listener.reset(4, 0, 0);
		player.eject();
		player.load(library.getCollection().get(0));
		player.eject();
		player.play();

		listener.stateChangedLatch.await(1, TimeUnit.SECONDS);
		assertThat(listener.stateChangedCount, is(4));
		assertThat(machine.getState().getIds(), contains(States.BUSY, States.PLAYING));

		listener.reset(2, 0, 0);
		player.stop();
		listener.stateChangedLatch.await(1, TimeUnit.SECONDS);
		assertThat(listener.stateChangedCount, is(2));
		assertLcdStatusIs("cd1 ");
	}

	@Test
	public void testPlayDeckOpenNoCd() throws Exception {
		listener.reset(2, 0, 0);
		player.eject();
		player.play();
		listener.stateChangedLatch.await(1, TimeUnit.SECONDS);
		assertThat(listener.stateChangedCount, is(2));
		assertThat(machine.getState().getIds(), contains(States.IDLE, States.CLOSED));
	}

	private void assertLcdStatusIs(String text) {
		assertThat(player.getLdcStatus(), is(text));
	}

	private void assertLcdStatusStartsWith(String text) {
		assertThat(player.getLdcStatus(), startsWith(text));
	}

	private void assertLcdStatusContains(String text) {
		assertThat(player.getLdcStatus(), containsString(text));
	}

	@SuppressWarnings("unchecked")
	@Before
	public void setup() {
		context = new AnnotationConfigApplicationContext();
		context.register(CommonConfiguration.class, Application.class, TestConfig.class);
		context.refresh();
		machine = context.getBean(StateMachineSystemConstants.DEFAULT_ID_STATEMACHINE, EnumStateMachine.class);
		player = context.getBean(CdPlayer.class);
		library = context.getBean(Library.class);
		listener = context.getBean(TestListener.class);
		machine.start();
	}

	@After
	public void clean() {
		machine.stop();
		context.close();
		context = null;
		machine = null;
		player = null;
		library = null;
		listener = null;
	}

	static class TestConfig {

		@Autowired
		private StateMachine<States,Events> machine;

		@Bean
		public StateMachineListener<States, Events> stateMachineListener() {
			TestListener listener = new TestListener();
			machine.addStateListener(listener);
			return listener;
		}

		@Bean
		public Library library() {
			// override library to make it easier to test
			Track cd1track1 = new Track("cd1track1", 3);
			Track cd1track2 = new Track("cd1track2", 3);
			Cd cd1 = new Cd("cd1", new Track[]{cd1track1,cd1track2});
			Track cd2track1 = new Track("cd2track1", 3);
			Track cd2track2 = new Track("cd2track2", 3);
			Cd cd2 = new Cd("cd2", new Track[]{cd2track1,cd2track2});
			return new Library(new Cd[]{cd1,cd2});
		}

	}

	static class TestListener extends StateMachineListenerAdapter<States, Events> {

		volatile CountDownLatch stateChangedLatch = new CountDownLatch(1);
		volatile CountDownLatch stateEnteredLatch = new CountDownLatch(2);
		volatile CountDownLatch stateExitedLatch = new CountDownLatch(0);
		volatile CountDownLatch transitionLatch = new CountDownLatch(0);
		volatile int stateChangedCount = 0;
		volatile int transitionCount = 0;
		List<State<States, Events>> statesEntered = new ArrayList<State<States,Events>>();
		List<State<States, Events>> statesExited = new ArrayList<State<States,Events>>();

		@Override
		public void stateChanged(State<States, Events> from, State<States, Events> to) {
			stateChangedLatch.countDown();
			stateChangedCount++;
		}

		@Override
		public void stateEntered(State<States, Events> state) {
			statesEntered.add(state);
			stateEnteredLatch.countDown();
		}

		@Override
		public void stateExited(State<States, Events> state) {
			statesExited.add(state);
			stateExitedLatch.countDown();
		}

		@Override
		public void transitionEnded(Transition<States, Events> transition) {
			transitionLatch.countDown();
			transitionCount++;
		}

		public void reset(int c1, int c2, int c3) {
			reset(c1, c2, c3, 0);
		}

		public void reset(int c1, int c2, int c3, int c4) {
			stateChangedLatch = new CountDownLatch(c1);
			stateEnteredLatch = new CountDownLatch(c2);
			stateExitedLatch = new CountDownLatch(c3);
			transitionLatch = new CountDownLatch(c4);
			stateChangedCount = 0;
			transitionCount = 0;
			statesEntered.clear();
			statesExited.clear();
		}

	}

}