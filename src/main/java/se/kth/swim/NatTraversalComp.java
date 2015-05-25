/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * GVoD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.kth.swim;

import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.kth.swim.croupier.CroupierPort;
import se.kth.swim.croupier.msg.CroupierSample;
import se.kth.swim.msg.net.NetMsg;
import se.kth.swim.nat.msg.NetNatPing;
import se.kth.swim.nat.msg.NetNatPong;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Header;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.impl.RelayHeader;
import se.sics.p2ptoolbox.util.network.impl.SourceHeader;

/**
 *
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class NatTraversalComp extends ComponentDefinition {

	private static final Logger log = LoggerFactory
			.getLogger(NatTraversalComp.class);
	private Negative<Network> local = provides(Network.class);
	private Positive<Network> network = requires(Network.class);
	private Positive<CroupierPort> croupier = requires(CroupierPort.class);
	private Positive<Timer> timer = requires(Timer.class);

	private final NatedAddress selfAddress;
	private final Random rand;
	private UUID heartBeatTimout;

	public NatTraversalComp(NatTraversalInit init) {
		this.selfAddress = init.selfAddress;
		this.heartBeatTimout = null;
		log.info("{} {} initiating...", new Object[] { selfAddress.getId(),
				(selfAddress.isOpen() ? "OPEN" : "NATED") });

		this.rand = new Random(init.seed);
		
		subscribe(handleStart, control);
		subscribe(handleStop, control);
		
		subscribe(handleOutgoingMsg, local);
		
		subscribe(handleCroupierSample, croupier);
		
		subscribe(handleHBTimeout, timer);
		subscribe(handleParentFailure, timer);
		
		subscribe(handleIncomingMsg, network);
		subscribe(handleNatPing, network);
		subscribe(handleNatPong, network);
	}

	private Handler<Start> handleStart = new Handler<Start>() {

		@Override
		public void handle(Start event) {
			log.info("{} starting...", new Object[] { selfAddress.getId() });
			if (!selfAddress.isOpen()) {
				log.info("Node {} setting periodic NAT Heartbeat",
						selfAddress.getId());
				heartBeatTimout = schedulePeriodicHB();
			}
		}

	};
	private Handler<Stop> handleStop = new Handler<Stop>() {

		@Override
		public void handle(Stop event) {
			if (heartBeatTimout != null)
				cancelPeriodicHB(heartBeatTimout);
			log.info("{} stopping...", new Object[] { selfAddress.getId() });
		}

	};

	private Handler<NetMsg<Object>> handleIncomingMsg = new Handler<NetMsg<Object>>() {

		@Override
		public void handle(NetMsg<Object> msg) {
			log.trace("{} received msg:{}", new Object[] { selfAddress.getId(),
					msg });
			Header<NatedAddress> header = msg.getHeader();
			if (header instanceof SourceHeader) {
				if (!selfAddress.isOpen()) {
					throw new RuntimeException(
							"source header msg received on nated node - nat traversal logic error");
				}
				SourceHeader<NatedAddress> sourceHeader = (SourceHeader<NatedAddress>) header;
				if (sourceHeader.getActualDestination().getParents()
						.contains(selfAddress)) {
					log.info("{} relaying message for:{}", new Object[] {
							selfAddress.getId(), sourceHeader.getSource() });
					RelayHeader<NatedAddress> relayHeader = sourceHeader
							.getRelayHeader();
					trigger(msg.copyMessage(relayHeader), network);
					return;
				} else {
					log.warn(
							"{} received weird relay message:{} - dropping it",
							new Object[] { selfAddress.getId(), msg });
					return;
				}
			} else if (header instanceof RelayHeader) {
				if (selfAddress.isOpen()) {
					throw new RuntimeException(
							"relay header msg received on open node - nat traversal logic error");
				}
				RelayHeader<NatedAddress> relayHeader = (RelayHeader<NatedAddress>) header;
				log.info(
						"{} delivering relayed message:{} from:{}",
						new Object[] { selfAddress.getId(), msg,
								relayHeader.getActualSource() });
				Header<NatedAddress> originalHeader = relayHeader
						.getActualHeader();
				trigger(msg.copyMessage(originalHeader), local);
				return;
			} else {
				log.info(
						"{} delivering direct message:{} from:{}",
						new Object[] { selfAddress.getId(), msg,
								header.getSource() });
				trigger(msg, local);
				return;
			}
		}

	};

	private Handler<NetMsg<Object>> handleOutgoingMsg = new Handler<NetMsg<Object>>() {

		@Override
		public void handle(NetMsg<Object> msg) {
			log.trace("{} sending msg:{}", new Object[] { selfAddress.getId(),
					msg });
			Header<NatedAddress> header = msg.getHeader();
			if (header.getDestination().isOpen()) {
				log.info("{} sending direct message:{} to:{}", new Object[] {
						selfAddress.getId(), msg, header.getDestination() });
				trigger(msg, network);
				return;
			} else {
				if (header.getDestination().getParents().isEmpty()) {
					throw new RuntimeException("nated node with no parents");
				}
				NatedAddress parent = randomNode(header.getDestination()
						.getParents());
				SourceHeader<NatedAddress> sourceHeader = new SourceHeader(
						header, parent);
				log.info("{} sending message:{} to relay:{}", new Object[] {
						selfAddress.getId(), msg, parent });
				trigger(msg.copyMessage(sourceHeader), network);
				return;
			}
		}

	};

	/**
	 * Handle timeout for periodic heartbeats
	 */
	private final Handler<HeartBeatTimeout> handleHBTimeout = new Handler<HeartBeatTimeout>() {
		@Override
		public void handle(HeartBeatTimeout event) {
			// Send PING to all parents
			Set<NatedAddress> parents = selfAddress.getParents();
			parents.forEach(x -> {
				// Schedule failure timeout and trigger NATPing
				UUID hbTimeout = scheduleParentFailureTimeout(x);
				log.info(
						"Node {} sending NatPing and setting timeout for node {}",
						new Object[] { selfAddress.getId(), x.getId() });
				trigger(new NetNatPing(selfAddress, x, hbTimeout), network);
			});
		}
	};

	/**
	 * Handle Ping from periodic heartbeats
	 */
	private final Handler<NetNatPing> handleNatPing = new Handler<NetNatPing>() {
		@Override
		public void handle(NetNatPing event) {
			log.info("Node {} received NatPing from node {}, responding...",
					new Object[] { selfAddress.getId(),
							event.getSource().getId() });
			// Respond to ping
			trigger(new NetNatPong(selfAddress, event.getSource(), event
					.getContent().getHBTimeoutId()), network);
		}
	};

	/**
	 * Handle pong replies
	 */
	private final Handler<NetNatPong> handleNatPong = new Handler<NetNatPong>() {
		@Override
		public void handle(NetNatPong event) {
			log.info(
					"Node {} received NatPong from node {}, canceling timeout!",
					new Object[] { selfAddress.getId(),
							event.getSource().getId() });
			// Cancel timeout set for that node
			cancelParentFailureTimeout(event.getContent().getHBTimeoutId());
		}
	};

	/**
	 * Handle parent failure timeout, parent is dead
	 */
	private final Handler<ParentFailureTimeout> handleParentFailure = new Handler<ParentFailureTimeout>() {
		@Override
		public void handle(ParentFailureTimeout event) {
			log.info(
					"Node {}, parent {} is DEAD!",
					new Object[] { selfAddress.getId(), event.getPeer().getId() });
		}
	};

	private Handler handleCroupierSample = new Handler<CroupierSample>() {
		@Override
		public void handle(CroupierSample event) {
			log.info("{} croupier public nodes:{}", selfAddress.getBaseAdr(),
					event.publicSample);
			// use this to change parent in case it died
			log.info("RECEIVED CROUPIER SAMPLE!!!");
		}
	};

	private NatedAddress randomNode(Set<NatedAddress> nodes) {
		int index = rand.nextInt(nodes.size());
		Iterator<NatedAddress> it = nodes.iterator();
		while (index > 0) {
			it.next();
			index--;
		}
		return it.next();
	}

	public static class NatTraversalInit extends Init<NatTraversalComp> {

		public final NatedAddress selfAddress;
		public final long seed;

		public NatTraversalInit(NatedAddress selfAddress, long seed) {
			this.selfAddress = selfAddress;
			this.seed = seed;
		}
	}

	private UUID schedulePeriodicHB() {
		SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(1000, 2500);
		HeartBeatTimeout hbtime = new HeartBeatTimeout(spt);
		spt.setTimeoutEvent(hbtime);
		UUID pingTimeoutId = hbtime.getTimeoutId();
		trigger(spt, timer);

		return pingTimeoutId;
	}

	private void cancelPeriodicHB(UUID timeoutId) {
		CancelTimeout ct = new CancelTimeout(timeoutId);
		trigger(ct, timer);
	}

	private static class HeartBeatTimeout extends Timeout {

		public HeartBeatTimeout(SchedulePeriodicTimeout request) {
			super(request);
		}
	}

	private UUID scheduleParentFailureTimeout(NatedAddress peer) {
		ScheduleTimeout st = new ScheduleTimeout(2000);
		ParentFailureTimeout pft = new ParentFailureTimeout(st, peer);
		st.setTimeoutEvent(pft);
		UUID failureTimeout = pft.getTimeoutId();
		trigger(st, timer);

		return failureTimeout;
	}

	private void cancelParentFailureTimeout(UUID timeoutId) {
		CancelTimeout ct = new CancelTimeout(timeoutId);
		trigger(ct, timer);
	}

	private static class ParentFailureTimeout extends Timeout {
		private final NatedAddress peer;

		public ParentFailureTimeout(ScheduleTimeout request, NatedAddress peer) {
			super(request);
			this.peer = peer;
		}

		public NatedAddress getPeer() {
			return peer;
		}
	}
}
