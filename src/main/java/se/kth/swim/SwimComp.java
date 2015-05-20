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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.kth.swim.msg.Status;
import se.kth.swim.msg.net.NetPing;
import se.kth.swim.msg.net.NetPong;
import se.kth.swim.msg.net.NetStatus;
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
import se.sics.p2ptoolbox.simulator.run.LauncherComp;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class SwimComp extends ComponentDefinition {

	private static final Logger log = LoggerFactory.getLogger(SwimComp.class);
	// λlogn times
	private static final Integer INFECT_FACTOR = 3;
	private static final Integer MEMBERSHIP_SIZE = 10;
	private static final Integer PIGGYBACK_SIZE = 3;

	private Positive<Network> network = requires(Network.class);
	private Positive<Timer> timer = requires(Timer.class);

	private final NatedAddress selfAddress;
	private final Set<NatedAddress> bootstrapNodes;
	private final NatedAddress aggregatorAddress;
	private MembershipList<MembershipListItem> membershipList;

	private UUID pingTimeoutId;
	private UUID statusTimeoutId;

	private int receivedPings = 0;

	public SwimComp(SwimInit init) {
		this.selfAddress = init.selfAddress;
		log.info("{} initiating...", selfAddress);
		this.bootstrapNodes = init.bootstrapNodes;
		this.aggregatorAddress = init.aggregatorAddress;
		// TODO Remove magic number!
		this.membershipList = new MembershipList<MembershipListItem>(10);

		subscribe(handleStart, control);
		subscribe(handleStop, control);
		subscribe(handlePing, network);
		subscribe(handlePingTimeout, timer);
		subscribe(handleStatusTimeout, timer);
		subscribe(handlePingFailure, timer);
		subscribe(handlePong, network);
	}

	private Handler<Start> handleStart = new Handler<Start>() {

		@Override
		public void handle(Start event) {
			log.info("{} starting...", new Object[] { selfAddress.getId() });

			if (!bootstrapNodes.isEmpty()) {

				// Add bootstrap nodes to local membership list
				for (NatedAddress node : bootstrapNodes) {
					membershipList.getQueue().push(
							new MembershipListItem(new Peer(node)));
					log.info("{} my bootstrap node: {}", selfAddress.getId(),
							node);
				}
				schedulePeriodicPing();
			}
			schedulePeriodicStatus();
		}

	};
	private Handler<Stop> handleStop = new Handler<Stop>() {

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

	private Handler<NetPing> handlePing = new Handler<NetPing>() {

		@Override
		public void handle(NetPing event) {
			log.info("{} received PING from:{}, {}",
					new Object[] { selfAddress.getId(),
							event.getHeader().getSource() });
			receivedPings++;

			System.out.println("Node: " + selfAddress.toString());
			System.out.println("Partial view received:");
			System.out.println(event.getContent().getPiggyback());

			// TODO Merge received view with local

			System.out.println("Local membership list:");
			System.out.println(membershipList);
			Collections.sort(membershipList.getQueue().getList(),
					new MembershipListInfectionSortPolicy());
			System.out.println("After sorting:");
			System.out.println(membershipList);

			// Send PONG - Dummy LinkedList
			trigger(new NetPong(selfAddress, event.getSource(),
					new MembershipList<Peer>(2), event.getContent()
							.getPingTimeoutUUID()), network);
		}

	};

	private Handler<NetPong> handlePong = new Handler<NetPong>() {

		@Override
		public void handle(NetPong event) {
			UUID pingTimeoutID = event.getContent().getPingTimeoutUUID();
			log.info("{} received PONG from: {} Timeout id: {}",
					new Object[] { selfAddress.getId(), event.getSource(),
							pingTimeoutID.toString() });
			cancelPingTimeout(pingTimeoutID, event.getSource());

			// TODO Merge received view with local
		}
	};

	private Handler<PingTimeout> handlePingTimeout = new Handler<PingTimeout>() {

		@Override
		public void handle(PingTimeout event) {
			/*
			 * for (NatedAddress partnerAddress : bootstrapNodes) {
			 * log.info("{} sending ping to partner:{}", new Object[] {
			 * selfAddress.getId(), partnerAddress }); trigger(new
			 * NetPing(selfAddress, partnerAddress, "lalakoko"), network); }
			 */

			// Pick random peer to ping
			// TODO round-robin selection
			// Random rand = LauncherComp.scenario.getRandom();
			Random rand = new Random();
			Integer randInt = rand.nextInt(membershipList.getQueue().getSize());
			MembershipListItem peer = membershipList.getQueue().getElement(
					randInt);
			log.info("{} sending PING to node: {}",
					new Object[] { selfAddress.getId(),
							peer.getPeer().getNode() });
			UUID pingTimeoutID = schedulePingTimeout(peer.getPeer().getNode());

			// Piggyback partial view of my membership list
			Collections.sort(membershipList.getQueue().getList(),
					new MembershipListInfectionSortPolicy());

			MembershipList<Peer> piggyback = PeerExchangeSelection.getPeers(
					peer.getPeer().getNode(), membershipList, PIGGYBACK_SIZE);

			trigger(new NetPing(selfAddress, peer.getPeer().getNode(),
					piggyback, pingTimeoutID), network);
		}

	};

	private Handler<PingFailureTimeout> handlePingFailure = new Handler<PingFailureTimeout>() {

		@Override
		public void handle(PingFailureTimeout event) {
			// TODO Initiate indirect ping protocol
			log.info("{} Did NOT received pong message from: {}", selfAddress,
					event.getPeer());
		}

	};
	private Handler<StatusTimeout> handleStatusTimeout = new Handler<StatusTimeout>() {

		@Override
		public void handle(StatusTimeout event) {
			log.info("{} sending status to aggregator:{}", new Object[] {
					selfAddress.getId(), aggregatorAddress });
			trigger(new NetStatus(selfAddress, aggregatorAddress, new Status(
					receivedPings)), network);
		}

	};

	// Ping timeout for Failure Detector
	private UUID schedulePingTimeout(NatedAddress destination) {
		log.info("{} Setting PING FD timeout for node: {}", selfAddress,
				destination);
		ScheduleTimeout st = new ScheduleTimeout(20000);
		PingFailureTimeout pft = new PingFailureTimeout(st, destination);
		st.setTimeoutEvent(pft);
		trigger(st, timer);

		return pft.getTimeoutId();
	}

	private void cancelPingTimeout(UUID pingTimeoutUUID, NatedAddress source) {
		log.info("{} Canceling PING FD timeout for node: {} with ID: {}",
				new Object[] { selfAddress, source, pingTimeoutUUID });
		CancelTimeout ct = new CancelTimeout(pingTimeoutUUID);
		trigger(ct, timer);
	}

	private void schedulePeriodicPing() {
		SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(1000, 2000);
		PingTimeout sc = new PingTimeout(spt);
		spt.setTimeoutEvent(sc);
		pingTimeoutId = sc.getTimeoutId();
		trigger(spt, timer);
	}

	private void cancelPeriodicPing() {
		CancelTimeout cpt = new CancelTimeout(pingTimeoutId);
		trigger(cpt, timer);
		pingTimeoutId = null;
	}

	private void schedulePeriodicStatus() {
		SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(10000, 10000);
		StatusTimeout sc = new StatusTimeout(spt);
		spt.setTimeoutEvent(sc);
		statusTimeoutId = sc.getTimeoutId();
		trigger(spt, timer);
	}

	private void cancelPeriodicStatus() {
		CancelTimeout cpt = new CancelTimeout(statusTimeoutId);
		trigger(cpt, timer);
		statusTimeoutId = null;
	}

	public static class SwimInit extends Init<SwimComp> {

		public final NatedAddress selfAddress;
		public final Set<NatedAddress> bootstrapNodes;
		public final NatedAddress aggregatorAddress;

		public SwimInit(NatedAddress selfAddress,
				Set<NatedAddress> bootstrapNodes, NatedAddress aggregatorAddress) {
			this.selfAddress = selfAddress;
			this.bootstrapNodes = bootstrapNodes;
			this.aggregatorAddress = aggregatorAddress;
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
		private NatedAddress peer;

		public PingFailureTimeout(ScheduleTimeout request, NatedAddress peer) {
			super(request);
			this.peer = peer;
		}

		public NatedAddress getPeer() {
			return peer;
		}
	}
}
