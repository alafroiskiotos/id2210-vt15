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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.kth.swim.msg.IndirectPing;
import se.kth.swim.msg.IndirectPong;
import se.kth.swim.msg.StartIndirectPing;
import se.kth.swim.msg.Status;
import se.kth.swim.msg.StopIndirectPing;
import se.kth.swim.msg.net.NetIndirectPing;
import se.kth.swim.msg.net.NetIndirectPong;
import se.kth.swim.msg.net.NetPing;
import se.kth.swim.msg.net.NetPong;
import se.kth.swim.msg.net.NetStartIndirectPing;
import se.kth.swim.msg.net.NetStatus;
import se.kth.swim.msg.net.NetStopIndirectPing;
import se.kth.swim.nat.events.NatPort;
import se.kth.swim.nat.events.NatRequest;
import se.kth.swim.nat.events.NatResponse;
import se.kth.swim.nat.events.NatUpdate;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class SwimComp extends ComponentDefinition {

	private static final Logger log = LoggerFactory.getLogger(SwimComp.class);
	// λlogn times
	private static final Integer INFECT_FACTOR = 50;
	private static final Integer PIGGYBACK_SIZE = 50;
	private static final Integer INDIRECT_PING_SIZE = 2;
	private Integer localSequenceNumber = 0;

	private Positive<Network> network = requires(Network.class);
	private final Positive<Timer> timer = requires(Timer.class);
	private Positive<NatPort> nat = requires(NatPort.class);

	private NatedAddress selfAddress;
	private final Set<NatedAddress> bootstrapNodes;
	private final NatedAddress aggregatorAddress;
	private List<Member> members;
	private Peer self;
	private Member selfMember;
	private final Random rand;

	private UUID pingTimeoutId;
	private UUID statusTimeoutId;

	private int receivedPings = 0;

	public SwimComp(SwimInit init) {
		this.selfAddress = init.selfAddress;
		log.info("{} initiating...", selfAddress);
		this.bootstrapNodes = init.bootstrapNodes;
		this.aggregatorAddress = init.aggregatorAddress;
		this.rand = new Random(init.getSeed());
		this.members = new ArrayList<>();
		this.self = new Peer(selfAddress, NodeState.ALIVE);
		selfMember = new Member(self);
		selfMember.setPingedTimes(Integer.MAX_VALUE);

		// We add ourself and we spread us in the beginning.
		members.add(selfMember);

		subscribe(handleStart, control);
		subscribe(handleStop, control);

		subscribe(handlePingTimeout, timer);
		subscribe(handleStatusTimeout, timer);
		subscribe(handlePingFailure, timer);
		subscribe(handleDeatTimeout, timer);

		subscribe(handlePing, network);
		subscribe(handlePong, network);
		subscribe(handleStartIndirectPing, network);
		subscribe(handleIndirectPing, network);
		subscribe(handleIndirectPong, network);
		subscribe(handleStopIndirectPing, network);

		subscribe(handleNatRequest, nat);
		subscribe(handleNatUpdate, nat);
	}

	private final Handler<Start> handleStart = new Handler<Start>() {

		@Override
		public void handle(Start event) {
			log.info("{} starting...", new Object[] { selfAddress.getId() });

			self.setIncarnation(self.getIncarnation() + 1);

			if (!bootstrapNodes.isEmpty()) {

				// Add bootstrap nodes to local membership list
				for (NatedAddress node : bootstrapNodes) {
					members.add(new Member(new Peer(node, NodeState.ALIVE)));
					log.info("{} my bootstrap node: {}", selfAddress.getId(),
							node);
				}
				schedulePeriodicPing();
			}
			//schedulePeriodicStatus();
		}
	};
	private final Handler<Stop> handleStop = new Handler<Stop>() {

		@Override
		public void handle(Stop event) {
			log.info("{} stopping...", new Object[] { selfAddress.getId() });
			if (pingTimeoutId != null) {
				cancelPeriodicPing();
			}
			if (statusTimeoutId != null) {
				cancelPeriodicStatus();
			}
		}

	};

	/**
	 * Handle PING messages. Generate piggyback with new changes and
	 * respond back. Increment infection time of piggybacked objects
	 */
	private final Handler<NetPing> handlePing = new Handler<NetPing>() {

		@Override
		public void handle(NetPing event) {

			if (isCausalOrNew(event.getSource().getId(), event.getContent()
					.getCounter())) {

				log.info("{} received PING from:{}, {}", new Object[] {
						selfAddress.getId(), event.getHeader().getSource() });

				receivedPings++;

				log.info("{} Partial view received: {}", selfAddress.getId(),
						event.getContent().getPiggyback());

				// Create piggyback view. Sort local view with infection
				// time and node status - DEAD status first
				Collections.sort(members, new MembersInfectionSortPolicy());
				List<Peer> piggyback = PeerExchangeSelection.getPeers(members,
						PIGGYBACK_SIZE, INFECT_FACTOR);

				// Increments the infection time of the piggybacked node's
				PeerExchangeSelection.updateInfectionTime(members, piggyback);

				// Merge received view with the local
				List<Peer> receivedView = event.getContent().getPiggyback();
				members = PeerExchangeSelection.merge(self, members,
						receivedView);

				log.info("{} Local membership list after PING merging: {}",
						selfAddress.getId(), members);
				log.info("{} Respond with PONG to {} with view: {}",
						new Object[] { selfAddress.getId(),
								event.getSource().getId(), piggyback });

				localSequenceNumber++;

				// Send PONG with changes - piggyback
				trigger(new NetPong(selfAddress, event.getSource(), piggyback,
						event.getContent().getPingTimeoutUUID(),
						localSequenceNumber), network);
			}
		}
	};

	/**
	 * Handle PONG messages. Merge received changes with local view
	 */
	private final Handler<NetPong> handlePong = new Handler<NetPong>() {

		@Override
		public void handle(NetPong event) {

			if (isCausalOrNew(event.getSource().getId(), event.getContent()
					.getCounter())) {
				
				log.info("{} received PONG from: {} Partial view received {}",
						new Object[] { selfAddress.getId(), event.getSource(),
								event.getContent().getView() });
				
				// Cancel ping timeout for failure detection
				UUID pingTimeoutID = event.getContent().getPingTimeoutUUID();
				cancelPingTimeout(pingTimeoutID, event.getSource());

				// Merge received view with local
				members = PeerExchangeSelection.merge(self, members, event
						.getContent().getView());

				log.info("{} Local after PONG MERGED membership list: {}",
						selfAddress.getId(), members);
			}
		}
	};

	/**
	 * Initiate PING - view exchange with a peer
	 */
	private final Handler<PingTimeout> handlePingTimeout = new Handler<PingTimeout>() {

		@Override
		public void handle(PingTimeout event) {

			// Select possible PING recipients in round-robin fashion
			// Select peers pinged less time
			List<Member> selectables = PeerExchangeSelection
					.getPingableTargets(self, members);

			if (!selectables.isEmpty()) {
				Member pingPeer = selectables.get(0);

				// Schedule timeout for the Failure Detector
				UUID pingTimeoutID = schedulePingTimeout(pingPeer.getPeer());

				// Increment ping time
				pingPeer.incrementPingedTimes();

				// Sort local view with infection time and node status - DEAD first
				Collections.sort(members, new MembersInfectionSortPolicy());
				// Get piggyback
				List<Peer> piggyback = PeerExchangeSelection.getPeers(members,
						PIGGYBACK_SIZE, INFECT_FACTOR);

				// Increments the infection time of the piggybacked node's
				PeerExchangeSelection.updateInfectionTime(members, piggyback);

				log.info("{} sending PING to node: {}. View Sending: {}",
						new Object[] { selfAddress.getId(),
								pingPeer.getPeer().getNode(), piggyback });

				localSequenceNumber++;

				// Send piggyback to recipient
				trigger(new NetPing(selfAddress, pingPeer.getPeer().getNode(),
						piggyback, pingTimeoutID, localSequenceNumber), network);
			}
		}
	};

	/**
	 * Handle event triggered when PONG has not been received.
	 * Initiate indirect ping protocol
	 */
	private final Handler<PingFailureTimeout> handlePingFailure = new Handler<PingFailureTimeout>() {

		@Override
		public void handle(PingFailureTimeout event) {
			log.info("{} Did NOT received pong message from: {}", selfAddress,
					event.getPeer());

			// Find the node that did not respond to our PING, in our local view
			int index = members.indexOf(new Member(event.getPeer()));

			if (index >= 0) {
				// Set its state SUSPECTED
				members.get(index).getPeer().setState(NodeState.SUSPECTED);

				// Random peer selection for indirect ping
				List<Peer> randomPeers = PeerExchangeSelection
						.getIndirectPingPeers(members, INDIRECT_PING_SIZE,
								rand, self);

				if (randomPeers.size() > 0) {
					// Set new timeout. After this period the node is declared DEAD
					UUID uuid = scheduleDeadTimeout(event.getPeer());

					localSequenceNumber++;

					// Send indirect ping to recipients
					randomPeers.forEach(x -> {
						trigger(new NetStartIndirectPing(selfAddress, x.getNode(),
								new StartIndirectPing(self, event.getPeer(), uuid,
										localSequenceNumber)), network);
					});
				}
			}

			log.info("{} membership list after PING FAILURE TIMEOUT: {}",
					new Object[] { selfAddress.getId(), members });
		}
	};

	/**
	 * Recipient of indirect ping should PING the suspected node
	 */
	private final Handler<NetStartIndirectPing> handleStartIndirectPing = new Handler<NetStartIndirectPing>() {

		@Override
		public void handle(NetStartIndirectPing event) {

			if (isCausalOrNew(event.getSource().getId(), event.getContent()
					.getCounter())) {
				log.info("Node {} received NetStartIndirectPing for suspected {}",
						selfAddress.getId(), event.getContent().getSuspectedPeer());

				localSequenceNumber++;

				// Send ping to suspected node. Include also initiator of
				// indirect ping
				trigger(new NetIndirectPing(selfAddress, event.getContent()
						.getSuspectedPeer().getNode(), new IndirectPing(event
						.getContent().getInitiatorPeer(), event.getContent()
						.getDeadPingTimeout(), localSequenceNumber)), network);
			}
		}
	};

	/**
	 * Suspected node handle the indirect ping
	 */
	private final Handler<NetIndirectPing> handleIndirectPing = new Handler<NetIndirectPing>() {

		@Override
		public void handle(NetIndirectPing event) {
			if (isCausalOrNew(event.getSource().getId(), event.getContent()
					.getCounter())) {
				localSequenceNumber++;

				// Send back indirect pong to indirect pinger
				trigger(new NetIndirectPong(selfAddress, event.getSource(),
						new IndirectPong(event.getContent().getIndirectPingRequester(),
								self, event.getContent().getDeadPingTimeout(),
								localSequenceNumber)), network);
			}
		}
	};

	/**
	 * Indirect pinger, handle indirect pong from suspected node
	 */
	private final Handler<NetIndirectPong> handleIndirectPong = new Handler<NetIndirectPong>() {

		@Override
		public void handle(NetIndirectPong event) {
			if (isCausalOrNew(event.getSource().getId(), event.getContent()
					.getCounter())) {

				localSequenceNumber++;

				// Respond back to the initiator. Suspected is Alive
				trigger(new NetStopIndirectPing(selfAddress, event.getContent()
						.getInitiatorPeer().getNode(), new StopIndirectPing(
								event.getContent().getSuspectedPeer(),
								event.getContent().getDeadTImeout(),
						localSequenceNumber)), network);
			}
		}
	};

	/**
	 * Initiator of indirect ping eventually receives a indirect pong.
	 * Suspected node is Alive
	 */
	private final Handler<NetStopIndirectPing> handleStopIndirectPing = new Handler<NetStopIndirectPing>() {

		@Override
		public void handle(NetStopIndirectPing event) {

			if (isCausalOrNew(event.getSource().getId(), event.getContent()
					.getCounter())) {
				log.info("Node {} received STOP INDIRECT PING for node {}",
						new Object[] { selfAddress.getId(),
								event.getContent().getSuspectedPeer() });
				
				// Find that node in our view
				Optional<Member> tmpPeer = members.stream()
						.filter(x -> x.getPeer().equals(event.getContent().getSuspectedPeer()))
						.findFirst();

				if (tmpPeer.isPresent()) {
					// Change node status from SUSPECTED to ALIVE
					// Reset infection time to spread the change
					tmpPeer.get().getPeer().setState(NodeState.ALIVE);
					tmpPeer.get().resetInfectionTime();
				}

				// Cancel the timeout that would declare that node DEAD
				cancelDeadTimeout(event.getContent().getDeadPingTimeout(),
						event.getContent().getSuspectedPeer().getNode());
			}
		}
	};

	/**
	 * When a node is suspected and did not receive an indirect pong,
	 * declare that node DEAD
	 */
	private final Handler<DeadTimeout> handleDeatTimeout = new Handler<DeadTimeout>() {

		@Override
		public void handle(DeadTimeout event) {
			log.info("Node {} declared peer DEAD with timeout ID {}",
					new Object[] { selfAddress.getId(), event.getTimeoutId() });

			// Find that node in our view
			Member dead = members.stream()
					.filter(x -> x.getPeer().equals(event.getDeadPeer()))
					.findFirst().get();

			// Change its status to DEAD and reset infection time
			dead.getPeer().setState(NodeState.DEAD);
			dead.resetInfectionTime();
		}
	};

	/**
	 * Send various statistics to Aggregator Component periodically
	 */
	private final Handler<StatusTimeout> handleStatusTimeout = new Handler<StatusTimeout>() {
		@Override
		public void handle(StatusTimeout event) {
			log.info("{} sending status to aggregator:{}", new Object[] {
					selfAddress.getId(), aggregatorAddress });

			int alive = (int) members
					.stream()
					.filter(x -> x.getPeer().getState().equals(NodeState.ALIVE))
					.count();

			int dead = (int) members.stream()
					.filter(x -> x.getPeer().getState().equals(NodeState.DEAD))
					.count();

			trigger(new NetStatus(selfAddress, aggregatorAddress, new Status(
					receivedPings, dead, alive)), network);
		}
	};

	/**
	 * Handle message from NAT traversal Component to filter our ALIVE nodes from
	 * Croupier sample for possible new NAT parents
	 */
	private final Handler<NatRequest> handleNatRequest = new Handler<NatRequest>() {
		@Override
		public void handle(NatRequest event) {
			
			// Find IDs of alive nodes
			List<Integer> alives = members.stream()
					.filter(x -> x.getPeer().getState().equals(NodeState.ALIVE))
					.map(x -> x.getPeer().getNode().getId())
					.collect(Collectors.toList());

			// Respond with the alive subset of the sample received
			List<NatedAddress> ret = event.getParents().stream()
					.filter(x -> alives.contains(x.getId()))
					.collect(Collectors.toList());

			StringBuilder sb = new StringBuilder();
			sb.append("{");
			event.getParents().forEach(x -> sb.append(",").append(x.getId()));
			sb.append("}");

			log.info("Node {} requires check for {}", new Object[] {
					selfAddress.getId(), sb.toString() });

			// Respond to NAT traversal component
			trigger(new NatResponse(ret), nat);
		}
	};

	/**
	 * Node has eventually changed NAT parents, so SWIM component should
	 * also change its self address and spread this information
	 */
	private final Handler<NatUpdate> handleNatUpdate = new Handler<NatUpdate>() {
		@Override
		public void handle(NatUpdate event) {
			// Store old incarnation number and remove ourself from the local view
			Integer oldIncarnation = selfMember.getPeer().getIncarnation();
			members.remove(selfMember);

			// Make a new self reference
			selfAddress = event.getNewNatedAddress();
			self = new Peer(selfAddress, NodeState.ALIVE);
			selfMember = new Member(self);
			selfMember.getPeer().setIncarnation(oldIncarnation + 1);
			selfMember.setPingedTimes(Integer.MAX_VALUE);

			// Add it to the local view and it will be spread to the overlay
			members.add(selfMember);
		}
	};

	/**
	 * Execute only if the source is a new node or if the sequence number of
	 * the message is larger than the last we have seen for that node
	 */
	private boolean isCausalOrNew(Integer peerId, Integer peerCounter) {
		Optional<Member> tmpPeer = members.stream()
				.filter(x -> x.getPeer().getNode().getId().equals(peerId))
				.findFirst();

		return !tmpPeer.isPresent()
				|| (tmpPeer.isPresent() && tmpPeer.get().isCausal(peerCounter));
	}

	/**
	 * Schedule Ping timeout for Failure Detector
	 * @param Peer that the timeout is set
	 * @return The unique ID of that timeout
	 */
	private UUID schedulePingTimeout(Peer destination) {
		log.info("{} Setting PING FD timeout for node: {}", selfAddress,
				destination);
		ScheduleTimeout st = new ScheduleTimeout(1200);
		PingFailureTimeout pft = new PingFailureTimeout(st, destination);
		st.setTimeoutEvent(pft);
		trigger(st, timer);

		return pft.getTimeoutId();
	}

	/**
	 * Schedule Ping timeout for Dead node detection
	 * @param Peer that the timeout is set
	 * @return The unique ID of that timeout
	 */
	private UUID scheduleDeadTimeout(Peer destination) {
		log.info("{} Setting DEAD timeout for node: {}", selfAddress,
				destination);
		ScheduleTimeout st = new ScheduleTimeout(2500);
		DeadTimeout dt = new DeadTimeout(st, destination);
		st.setTimeoutEvent(dt);
		trigger(st, timer);

		return dt.getTimeoutId();
	}

	/**
	 * Cancel the timeout for suspecting a node
	 * @param The ping timeout for a node
	 * @param The source address for the node
	 */
	private void cancelPingTimeout(UUID pingTimeoutUUID, NatedAddress source) {
		log.info("{} Canceling PING FD timeout for node: {} with ID: {}",
				new Object[] { selfAddress, source, pingTimeoutUUID });
		CancelTimeout ct = new CancelTimeout(pingTimeoutUUID);
		trigger(ct, timer);
	}

	/**
	 * Cancel the timeout to declare a node dead
	 * @param The dead timeout for the suspected
	 * @param Source address of the suspected node
	 */
	private void cancelDeadTimeout(UUID deadTimeoutUUID, NatedAddress source) {
		log.info("{} Canceling DEAD timeout for node: {} with ID: {}",
				new Object[] { selfAddress, source, deadTimeoutUUID });
		CancelTimeout ct = new CancelTimeout(deadTimeoutUUID);
		trigger(ct, timer);
	}

	/**
	 * Schedule periodic timer to initiate ping message
	 */
	private void schedulePeriodicPing() {
		SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(1000, 2000);
		PingTimeout sc = new PingTimeout(spt);
		spt.setTimeoutEvent(sc);
		pingTimeoutId = sc.getTimeoutId();
		trigger(spt, timer);
	}

	/**
	 * Cancel period ping timeout when the component terminates
	 */
	private void cancelPeriodicPing() {
		CancelTimeout cpt = new CancelTimeout(pingTimeoutId);
		trigger(cpt, timer);
		pingTimeoutId = null;
	}

	/**
	 * Schedule timeout to send statistics to Aggregator component
	 */
	private void schedulePeriodicStatus() {
		SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(10000, 10000);
		StatusTimeout sc = new StatusTimeout(spt);
		spt.setTimeoutEvent(sc);
		statusTimeoutId = sc.getTimeoutId();
		trigger(spt, timer);
	}

	/**
	 * Cancel timout for Aggregator when the component terminates
	 */
	private void cancelPeriodicStatus() {
		CancelTimeout cpt = new CancelTimeout(statusTimeoutId);
		trigger(cpt, timer);
		statusTimeoutId = null;
	}

	public static class SwimInit extends Init<SwimComp> {

		public final NatedAddress selfAddress;
		public final Set<NatedAddress> bootstrapNodes;
		public final NatedAddress aggregatorAddress;
		private final long seed;

		public SwimInit(NatedAddress selfAddress,
				Set<NatedAddress> bootstrapNodes,
				NatedAddress aggregatorAddress, long seed) {
			this.selfAddress = selfAddress;
			this.bootstrapNodes = bootstrapNodes;
			this.aggregatorAddress = aggregatorAddress;
			this.seed = seed;
		}

		public long getSeed() {
			return seed;
		}
	}

	private static class StatusTimeout extends Timeout {

		public StatusTimeout(SchedulePeriodicTimeout request) {
			super(request);
		}
	}

	private static class PingTimeout extends Timeout {

		public PingTimeout(SchedulePeriodicTimeout request) {
			super(request);
		}
	}

	private static class PingFailureTimeout extends Timeout {
		private final Peer peer;

		public PingFailureTimeout(ScheduleTimeout request, Peer peer) {
			super(request);
			this.peer = peer;
		}

		public Peer getPeer() {
			return peer;
		}
	}

	private static class DeadTimeout extends Timeout {
		private final Peer peer;

		public DeadTimeout(ScheduleTimeout request, Peer peer) {
			super(request);
			this.peer = peer;
		}

		public Peer getDeadPeer() {
			return peer;
		}
	}
}
