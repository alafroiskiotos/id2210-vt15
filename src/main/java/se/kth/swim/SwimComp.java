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
import java.util.HashSet;
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
import se.sics.p2ptoolbox.util.network.NatType;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicNatedAddress;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class SwimComp extends ComponentDefinition {

	private static final Logger log = LoggerFactory.getLogger(SwimComp.class);
	// λlogn times
	private static final Integer INFECT_FACTOR = 10;
	private static final Integer PIGGYBACK_SIZE = 10;
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

	private final Handler<NetPing> handlePing = new Handler<NetPing>() {

		@Override
		public void handle(NetPing event) {

			if (isCausalOrNew(event.getSource().getId(), event.getContent().getCounter())) {

				log.info("{} received PING from:{}, {}", new Object[] {
						selfAddress.getId(), event.getHeader().getSource() });
				
        receivedPings++;

				log.info("{} Partial view received: {}", selfAddress.getId(),
						event.getContent().getPiggyback());

				// Create piggyback view
				Collections.sort(members, new MembersInfectionSortPolicy());
        
				List<Peer> piggyback = PeerExchangeSelection.getPeers(members, PIGGYBACK_SIZE, INFECT_FACTOR);

				// Increments the infection time of the piggybacked node's
				PeerExchangeSelection.updateInfectionTime(members, piggyback);


				// Add the PING requester to the local view
				List<Peer> receivedView = event.getContent().getPiggyback();
				
        // We consider also the source when we merge
        // We don't need to store the pinger! We will get its entry from someone else
        //receivedView.add(new Peer(event.getSource(), NodeState.ALIVE));
        
				// Merge received view with local
				members = PeerExchangeSelection.merge(self, members, receivedView);

        log.info("{} Local membership list after PING merging: {}", selfAddress.getId(), members);
        log.info("{} Respond with PONG to {} with view: {}", new Object[] {selfAddress.getId(),
          event.getSource().getId(), piggyback});

				localSequenceNumber++;

				// Send PONG with a partial view - piggyback
				trigger(new NetPong(selfAddress, event.getSource(), piggyback,
						event.getContent().getPingTimeoutUUID(),
						localSequenceNumber), network);
			}
		}
	};

	private final Handler<NetPong> handlePong = new Handler<NetPong>() {

		@Override
		public void handle(NetPong event) {

			if (isCausalOrNew(event.getSource().getId(), event.getContent().getCounter())) {
				UUID pingTimeoutID = event.getContent().getPingTimeoutUUID();
				log.info("{} received PONG from: {} Partial view received {}",
						new Object[] { selfAddress.getId(), event.getSource(),
								event.getContent().getView() });
        
				cancelPingTimeout(pingTimeoutID, event.getSource());

				// Merge received view with local
				members = PeerExchangeSelection.merge(self, members, event.getContent().getView());

				log.info("{} Local after PONG MERGED membership list: {}",
						selfAddress.getId(), members);
			}
		}
	};

	private final Handler<PingTimeout> handlePingTimeout = new Handler<PingTimeout>() {

		@Override
		public void handle(PingTimeout event) {

			List<Peer> selectables = PeerExchangeSelection.getPingableTargets(self, members);

			if (!selectables.isEmpty()) {
				Integer randInt = rand.nextInt(selectables.size());
				Peer peer = selectables.get(randInt);
				
				// Schedule timeout for the Failure Detector
				UUID pingTimeoutID = schedulePingTimeout(peer);

				// Sort my partial view
				Collections.sort(members, new MembersInfectionSortPolicy());

				List<Peer> piggyback = PeerExchangeSelection.getPeers(members, 	PIGGYBACK_SIZE, INFECT_FACTOR);

				// Increments the infection time of the piggybacked node's
				PeerExchangeSelection.updateInfectionTime(members, piggyback);

        log.info("{} sending PING to node: {}. View Sending: {}", new Object[] {
          selfAddress.getId(),peer.getNode(), piggyback });

				localSequenceNumber++;

				// Piggyback partial view of my membership list
				trigger(new NetPing(selfAddress, peer.getNode(), piggyback,
						pingTimeoutID, localSequenceNumber), network);
			}
		}
	};

	private final Handler<PingFailureTimeout> handlePingFailure = new Handler<PingFailureTimeout>() {

		@Override
		public void handle(PingFailureTimeout event) {
			// TODO Initiate indirect ping protocol
			log.info("{} Did NOT received pong message from: {}", selfAddress,
					event.getPeer());

			int index = members.indexOf(new Member(event.getPeer()));

			if (index >= 0) {
				members.get(index).getPeer().setState(NodeState.SUSPECTED);
				log.info("SUSPECTED -> " + event.getPeer().toString());

				// Random peer selection for indirect ping
				List<Peer> randomPeers = PeerExchangeSelection.getIndirectPingPeers(members, INDIRECT_PING_SIZE, rand, self);

				if (randomPeers.size() > 0) {
					// Set a new timeout -> DEAD timeout
					UUID uuid = scheduleDeadTimeout(event.getPeer());

					localSequenceNumber++;

					// Indirect ping
					randomPeers.forEach(
            x -> {
              trigger(new NetStartIndirectPing(selfAddress, x.getNode(),
                  new StartIndirectPing(self, event.getPeer(), uuid, localSequenceNumber)),
                network);
              
              log.info("Receiver of Indirect Ping is peer: {}", x.getNode().getId());
          });
				}
			}

			log.info("{} membership list after PING FAILURE TIMEOUT: {}",
					new Object[] { selfAddress.getId(), members });
		}
	};

	private final Handler<NetStartIndirectPing> handleStartIndirectPing = new Handler<NetStartIndirectPing>() {

		@Override
		public void handle(NetStartIndirectPing event) {

			if (isCausalOrNew(event.getSource().getId(), event.getContent()
					.getCounter())) {
				// Sends the indirect ping
				log.info(
						"Node {} received NetStartIndirectPing for suspected {}",
						selfAddress.getId(), event.getContent()
								.getSuspectedPeer());

				localSequenceNumber++;

				trigger(new NetIndirectPing(selfAddress, event.getContent()
						.getSuspectedPeer().getNode(), new IndirectPing(event
						.getContent().getInitiatorPeer(), event.getContent()
						.getDeadPingTimeout(), localSequenceNumber)), network);
			}
		}
	};

	private final Handler<NetIndirectPing> handleIndirectPing = new Handler<NetIndirectPing>() {

		@Override
		public void handle(NetIndirectPing event) {
			if (isCausalOrNew(event.getSource().getId(), event.getContent()
					.getCounter())) {
				localSequenceNumber++;

				// The indirect pong is sent back to the indirect pinger.
				trigger(new NetIndirectPong(selfAddress, event.getSource(),
						new IndirectPong(event.getContent()
								.getIndirectPingRequester(), self, event
								.getContent().getDeadPingTimeout(),
								localSequenceNumber)), network);
			}
		}
	};

	private final Handler<NetIndirectPong> handleIndirectPong = new Handler<NetIndirectPong>() {

		@Override
		public void handle(NetIndirectPong event) {
			if (isCausalOrNew(event.getSource().getId(), event.getContent()
					.getCounter())) {

				localSequenceNumber++;

				trigger(new NetStopIndirectPing(selfAddress, event.getContent()
						.getInitiatorPeer().getNode(), new StopIndirectPing(
						event.getContent().getSuspectedPeer(), event
								.getContent().getDeadTImeout(),
						localSequenceNumber)), network);
			}
		}
	};

	private final Handler<NetStopIndirectPing> handleStopIndirectPing = new Handler<NetStopIndirectPing>() {

		@Override
		public void handle(NetStopIndirectPing event) {

			if (isCausalOrNew(event.getSource().getId(), event.getContent()
					.getCounter())) {
				log.info("Node {} received STOP INDIRECT PING for node {}",
						new Object[] { selfAddress.getId(),
								event.getContent().getSuspectedPeer() });
				Optional<Member> tmpPeer = members.stream()
						.filter(x -> x.getPeer().equals(event.getContent().getSuspectedPeer()))
						.findFirst();

				if (tmpPeer.isPresent()) {
					tmpPeer.get().getPeer().setState(NodeState.ALIVE);
					tmpPeer.get().setInfectionTime(0);
				}

				cancelDeadTimeout(event.getContent().getDeadPingTimeout(),
						event.getContent().getSuspectedPeer().getNode());
			}
		}
	};

	private final Handler<DeadTimeout> handleDeatTimeout = new Handler<DeadTimeout>() {

		@Override
		public void handle(DeadTimeout event) {
			log.info("Node {} declared peer DEAD with timeout ID {}",
					new Object[] { selfAddress.getId(), event.getTimeoutId() });
			
      Member dead = members.stream()
					.filter(x -> x.getPeer().equals(event.getDeadPeer()))
					.findFirst().get();

			dead.getPeer().setState(NodeState.DEAD);
			dead.resetInfectionTime();
		}
	};

	private final Handler<StatusTimeout> handleStatusTimeout = new Handler<StatusTimeout>() {
		@Override
		public void handle(StatusTimeout event) {
			log.info("{} sending status to aggregator:{}", new Object[] {
					selfAddress.getId(), aggregatorAddress });
      
      int alive = (int) members.stream()
        .filter(x -> x.getPeer().getState().equals(NodeState.ALIVE))
        .count();
      
      int dead = (int) members.stream()
        .filter(x -> x.getPeer().getState().equals(NodeState.DEAD))
        .count();
      
			trigger(new NetStatus(selfAddress, aggregatorAddress, new Status(
					receivedPings, dead, alive)), network);
		}
	};
  
  private final Handler<NatRequest> handleNatRequest = new Handler<NatRequest>() {
    @Override
    public void handle(NatRequest event) {
      System.out.println("RECIEVED MESSAGE!!!");
      List<Integer> alives = members.stream()
        .filter(x -> x.getPeer().getState().equals(NodeState.ALIVE))
        .map(x -> x.getPeer().getNode().getId())
        .collect(Collectors.toList());
      
      List<NatedAddress> ret = event.getParents().stream()
        .filter(x -> alives.contains(x.getId()))
        .collect(Collectors.toList());
      
      StringBuilder sb = new StringBuilder();
      sb.append("{"); 
      event.getParents().forEach(x -> sb.append(",").append(x.getId()));
      sb.append("}");
      
      log.info("Node {} requires check for {}", new Object[]{selfAddress.getId(), sb.toString()});
      
      trigger(new NatResponse(ret), nat);
    }
};
  
  private final Handler<NatUpdate> handleNatUpdate = new Handler<NatUpdate>() {
    @Override
    public void handle(NatUpdate event) {
    	Integer oldIncarnation = selfMember.getPeer().getIncarnation();
      members.remove(selfMember);
    	
    	selfAddress = event.getNewNatedAddress();
    	self = new Peer(selfAddress, NodeState.ALIVE);
    	selfMember = new Member(self);
    	selfMember.getPeer().setIncarnation(oldIncarnation + 1);
    	
    	members.add(selfMember);
    	
    	
    	log.info("Node {} my new parents are: {}", selfAddress.getId(), self.getNode().getParents());
    	
    }
  };

	// Execute only if the source is a new node or
	// if the sequence number of the message is larger
	// than the last we have seen for that node
	private boolean isCausalOrNew(Integer peerId, Integer peerCounter) {
		Optional<Member> tmpPeer = members.stream()
				.filter(x -> x.getPeer().getNode().getId().equals(peerId))
				.findFirst();

		return !tmpPeer.isPresent() || 
      (tmpPeer.isPresent() && tmpPeer.get().isCausal(peerCounter));
	}

	// Ping timeout for Failure Detector
	private UUID schedulePingTimeout(Peer destination) {
		log.info("{} Setting PING FD timeout for node: {}", selfAddress,
				destination);
		ScheduleTimeout st = new ScheduleTimeout(1500);
		PingFailureTimeout pft = new PingFailureTimeout(st, destination);
		st.setTimeoutEvent(pft);
		trigger(st, timer);

		return pft.getTimeoutId();
	}

	// Ping timeout for Dead node detection
	private UUID scheduleDeadTimeout(Peer destination) {
		log.info("{} Setting DEAD timeout for node: {}", selfAddress,
				destination);
		ScheduleTimeout st = new ScheduleTimeout(10000);
		DeadTimeout dt = new DeadTimeout(st, destination);
		st.setTimeoutEvent(dt);
		trigger(st, timer);

		return dt.getTimeoutId();
	}

	private void cancelPingTimeout(UUID pingTimeoutUUID, NatedAddress source) {
		log.info("{} Canceling PING FD timeout for node: {} with ID: {}",
				new Object[] { selfAddress, source, pingTimeoutUUID });
		CancelTimeout ct = new CancelTimeout(pingTimeoutUUID);
		trigger(ct, timer);
	}

	private void cancelDeadTimeout(UUID deadTimeoutUUID, NatedAddress source) {
		log.info("{} Canceling DEAD timeout for node: {} with ID: {}",
				new Object[] { selfAddress, source, deadTimeoutUUID });
		CancelTimeout ct = new CancelTimeout(deadTimeoutUUID);
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
		private final long seed;

		public SwimInit(NatedAddress selfAddress,
				Set<NatedAddress> bootstrapNodes, NatedAddress aggregatorAddress, long seed) {
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
